param(
    [string]$OutputPath,
    [Security.SecureString]$BackupPassword
)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$sourceKey = Join-Path $projectRoot '.signing\bagimliligim-upload.p12'
$sourcePasswordFile = Join-Path $projectRoot '.signing\password.dpapi.xml'
$keyAlias = 'bagimliligim-upload'
if (-not (Test-Path -LiteralPath $sourceKey) -or -not (Test-Path -LiteralPath $sourcePasswordFile)) {
    throw 'The existing upload key and its DPAPI password file are both required.'
}
if (-not $OutputPath) {
    $OutputPath = Join-Path $projectRoot ('.signing\bagimliligim-portable-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.p12')
}
$OutputPath = [IO.Path]::GetFullPath($OutputPath)
if (Test-Path -LiteralPath $OutputPath) { throw 'The backup destination already exists. Choose a new filename.' }
if (-not (Test-Path -LiteralPath ([IO.Path]::GetDirectoryName($OutputPath)) -PathType Container)) {
    throw 'Create the destination directory before running this command.'
}
$javaRuntime = 'C:\Program Files\Android\Android Studio\jbr'
if (-not (Test-Path -LiteralPath (Join-Path $javaRuntime 'bin\keytool.exe'))) { $javaRuntime = $env:JAVA_HOME }
if (-not $javaRuntime -or -not (Test-Path -LiteralPath (Join-Path $javaRuntime 'bin\keytool.exe'))) {
    throw 'Set JAVA_HOME to an installed JDK that contains keytool.'
}
$keytool = Join-Path $javaRuntime 'bin\keytool.exe'
$variableNames = @('BAGIMLILIGIM_SOURCE_PASSWORD', 'BAGIMLILIGIM_BACKUP_PASSWORD')
$previousEnvironment = @{}
foreach ($name in $variableNames) { $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    $interactive = $null -eq $BackupPassword
    if ($interactive) {
        $BackupPassword = Read-Host 'New portable backup password (at least 6 characters; keep it in your password manager)' -AsSecureString
    }
    $backupCredential = New-Object Management.Automation.PSCredential('backup', $BackupPassword)
    $env:BAGIMLILIGIM_BACKUP_PASSWORD = $backupCredential.GetNetworkCredential().Password
    if ($env:BAGIMLILIGIM_BACKUP_PASSWORD.Length -lt 6) { throw 'PKCS12 requires a password with at least 6 characters.' }
    if ($interactive) {
        $confirmation = Read-Host 'Repeat the backup password' -AsSecureString
        $confirmationCredential = New-Object Management.Automation.PSCredential('confirm', $confirmation)
        if ($env:BAGIMLILIGIM_BACKUP_PASSWORD -cne $confirmationCredential.GetNetworkCredential().Password) {
            throw 'The passwords do not match. No backup was created.'
        }
    }
    $sourcePassword = Import-Clixml -LiteralPath $sourcePasswordFile
    $sourceCredential = New-Object Management.Automation.PSCredential('source', $sourcePassword)
    $env:BAGIMLILIGIM_SOURCE_PASSWORD = $sourceCredential.GetNetworkCredential().Password
    & $keytool -importkeystore -noprompt -srckeystore $sourceKey -srcstoretype PKCS12 -srcalias $keyAlias -srcstorepass:env BAGIMLILIGIM_SOURCE_PASSWORD -srckeypass:env BAGIMLILIGIM_SOURCE_PASSWORD -destkeystore $OutputPath -deststoretype PKCS12 -destalias $keyAlias -deststorepass:env BAGIMLILIGIM_BACKUP_PASSWORD -destkeypass:env BAGIMLILIGIM_BACKUP_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Key export failed. The original key was not modified.' }
    $originalCertificate = & $keytool -exportcert -rfc -keystore $sourceKey -alias $keyAlias -storepass:env BAGIMLILIGIM_SOURCE_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Cannot verify the original certificate.' }
    $backupCertificate = & $keytool -exportcert -rfc -keystore $OutputPath -alias $keyAlias -storepass:env BAGIMLILIGIM_BACKUP_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Cannot open the backup with the selected password.' }
    if (($originalCertificate -join "`n") -cne ($backupCertificate -join "`n")) {
        throw 'Certificate verification failed. Do not use this backup.'
    }
    Write-Host "Verified portable backup: $OutputPath"
    Write-Host "Alias: $keyAlias"
    Write-Host 'Copy the encrypted .p12 to secure storage outside this computer. Keep its password separately.'
    Write-Host 'The original signing key and its local password are unchanged.'
} finally {
    foreach ($name in $variableNames) { [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process') }
    $backupCredential = $null
    $sourceCredential = $null
    $confirmationCredential = $null
    $BackupPassword = $null
    $sourcePassword = $null
    $confirmation = $null
}
