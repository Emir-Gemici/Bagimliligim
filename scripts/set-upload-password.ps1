param(
    [Parameter(Mandatory = $true)]
    [string]$ReferenceKeyStore
)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$signingRoot = Join-Path $projectRoot '.signing'
$keyPath = Join-Path $signingRoot 'bagimliligim-upload.p12'
$passwordPath = Join-Path $signingRoot 'password.dpapi.xml'
$referencePath = (Resolve-Path -LiteralPath $ReferenceKeyStore).Path
if (-not (Test-Path -LiteralPath $keyPath) -or -not (Test-Path -LiteralPath $passwordPath)) {
    throw 'The current upload key and its local DPAPI password are required.'
}
$javaRuntime = 'C:\Program Files\Android\Android Studio\jbr'
if (-not (Test-Path -LiteralPath (Join-Path $javaRuntime 'bin\keytool.exe'))) { $javaRuntime = $env:JAVA_HOME }
if (-not $javaRuntime -or -not (Test-Path -LiteralPath (Join-Path $javaRuntime 'bin\keytool.exe'))) {
    throw 'Set JAVA_HOME to an installed JDK that contains keytool.'
}
$keytool = Join-Path $javaRuntime 'bin\keytool.exe'
$oldEnvironment = [Environment]::GetEnvironmentVariable('BAGIMLILIGIM_REFERENCE_PASSWORD', 'Process')
$stamp = [guid]::NewGuid().ToString('N')
$stagedKey = Join-Path $signingRoot ("new-$stamp.p12")
$stagedPassword = Join-Path $signingRoot ("new-$stamp.dpapi.xml")
$previousKey = Join-Path $signingRoot ("previous-$stamp.p12")
$previousPassword = Join-Path $signingRoot ("previous-$stamp.dpapi.xml")
try {
    $verified = $false
    for ($attempt = 1; $attempt -le 5; $attempt++) {
        $candidate = Read-Host "Reference keystore password ($attempt/5; input is hidden)" -AsSecureString
        $credential = New-Object Management.Automation.PSCredential('reference', $candidate)
        $env:BAGIMLILIGIM_REFERENCE_PASSWORD = $credential.GetNetworkCredential().Password
        if ($env:BAGIMLILIGIM_REFERENCE_PASSWORD.Length -lt 6) {
            Write-Host 'Use the existing keystore password (at least 6 characters).'
            continue
        }
        $previousPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            & $keytool -list -keystore $referencePath -storepass:env BAGIMLILIGIM_REFERENCE_PASSWORD *> $null
            $verified = $LASTEXITCODE -eq 0
        } finally { $ErrorActionPreference = $previousPreference }
        if ($verified) { break }
        Write-Host 'The reference keystore could not be opened with that password. No files changed.'
    }
    if (-not $verified) { throw 'No valid password entered. All original files are unchanged.' }
    $beforeKey = (Get-FileHash -LiteralPath $keyPath -Algorithm SHA256).Hash
    $beforePassword = (Get-FileHash -LiteralPath $passwordPath -Algorithm SHA256).Hash
    & (Join-Path $PSScriptRoot 'backup-upload-key.ps1') -OutputPath $stagedKey -BackupPassword $candidate
    $candidate | Export-Clixml -LiteralPath $stagedPassword
    if ($beforeKey -ne (Get-FileHash -LiteralPath $keyPath -Algorithm SHA256).Hash -or
        $beforePassword -ne (Get-FileHash -LiteralPath $passwordPath -Algorithm SHA256).Hash) {
        throw 'Signing files changed while exporting. Close other signing processes and retry.'
    }
    [IO.File]::Replace($stagedKey, $keyPath, $previousKey)
    try {
        [IO.File]::Replace($stagedPassword, $passwordPath, $previousPassword)
    } catch {
        [IO.File]::Replace($previousKey, $keyPath, $null)
        throw
    }
    Write-Host 'Success: Bagimliligim now uses the verified reference keystore password.'
    Write-Host 'The private key, certificate, alias and reference keystore are unchanged.'
    Write-Host "Keep this file and its password: $keyPath"
    Write-Host 'Refresh any earlier backup copies. Previous local files remain in .signing for recovery.'
} finally {
    [Environment]::SetEnvironmentVariable('BAGIMLILIGIM_REFERENCE_PASSWORD', $oldEnvironment, 'Process')
    $credential = $null
    $candidate = $null
}
