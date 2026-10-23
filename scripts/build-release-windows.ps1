param(
    [string[]]$Tasks = @(':app:bundleRelease', ':app:assembleRelease', ':app:verifyPrivacyManifests')
)

$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$signingDirectory = Join-Path $projectRoot '.signing'
$keyStorePath = Join-Path $signingDirectory 'bagimliligim-upload.p12'
$passwordPath = Join-Path $signingDirectory 'password.dpapi.xml'
$javaRuntime = 'C:\Program Files\Android\Android Studio\jbr'
$signingVariables = @('JAVA_HOME', 'SWIPEMETER_KEYSTORE', 'SWIPEMETER_STORE_PASSWORD', 'SWIPEMETER_KEY_ALIAS', 'SWIPEMETER_KEY_PASSWORD')
$oldEnvironment = @{}
foreach ($variable in $signingVariables) { $oldEnvironment[$variable] = [Environment]::GetEnvironmentVariable($variable, 'Process') }

try {
    if (-not (Test-Path -LiteralPath (Join-Path $javaRuntime 'bin\keytool.exe'))) {
        $javaRuntime = $env:JAVA_HOME
    }
    if (-not $javaRuntime -or -not (Test-Path -LiteralPath (Join-Path $javaRuntime 'bin\keytool.exe'))) {
        throw 'A JDK with keytool is required. Set JAVA_HOME to JDK 17 or 21.'
    }
    $env:JAVA_HOME = $javaRuntime
    $hasKey = Test-Path -LiteralPath $keyStorePath
    $hasPassword = Test-Path -LiteralPath $passwordPath
    if ($hasKey -ne $hasPassword) {
        throw 'The local signing files are incomplete. Restore the existing key and password; do not replace a published signing identity.'
    }
    if (-not $hasKey) {
        New-Item -ItemType Directory -Path $signingDirectory -Force | Out-Null
        $identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
        $access = New-Object Security.AccessControl.DirectorySecurity
        $access.SetAccessRuleProtection($true, $false)
        $access.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')))
        Set-Acl -LiteralPath $signingDirectory -AclObject $access
        $random = [Security.Cryptography.RandomNumberGenerator]::Create()
        try {
            $bytes = New-Object byte[] 32
            $random.GetBytes($bytes)
            $securePassword = ConvertTo-SecureString -String ([Convert]::ToBase64String($bytes)) -AsPlainText -Force
        } finally { $random.Dispose() }
        # Windows DPAPI binds this encrypted password to this user and machine.
        $securePassword | Export-Clixml -LiteralPath $passwordPath
    } else {
        $securePassword = Import-Clixml -LiteralPath $passwordPath
    }
    $credential = New-Object System.Management.Automation.PSCredential('upload', $securePassword)
    $env:SWIPEMETER_STORE_PASSWORD = $credential.GetNetworkCredential().Password
    $env:SWIPEMETER_KEY_PASSWORD = $env:SWIPEMETER_STORE_PASSWORD
    $env:SWIPEMETER_KEYSTORE = '.signing/bagimliligim-upload.p12'
    $env:SWIPEMETER_KEY_ALIAS = 'bagimliligim-upload'
    if (-not $hasKey) {
        & (Join-Path $javaRuntime 'bin\keytool.exe') -genkeypair -noprompt -storetype PKCS12 -keystore $keyStorePath -alias $env:SWIPEMETER_KEY_ALIAS -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 10000 -dname 'CN=Bagimliligim Upload' -storepass:env SWIPEMETER_STORE_PASSWORD -keypass:env SWIPEMETER_KEY_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Upload key generation failed. Existing signing files were preserved.' }
    }
    & (Join-Path $javaRuntime 'bin\keytool.exe') -exportcert -rfc -keystore $keyStorePath -alias $env:SWIPEMETER_KEY_ALIAS -storepass:env SWIPEMETER_STORE_PASSWORD -file (Join-Path $signingDirectory 'upload-certificate.pem')
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read the existing upload certificate.' }
    # Use a child PowerShell because build-windows.ps1 exits with Gradle's status.
    $scriptPath = Join-Path $PSScriptRoot 'build-windows.ps1'
    $escapedPath = $scriptPath.Replace("'", "''")
    $taskArguments = ($Tasks | ForEach-Object { "'" + $_.Replace("'", "''") + "'" }) -join ','
    & powershell.exe -NoProfile -Command "& '$escapedPath' -Tasks @($taskArguments)"
    if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
} finally {
    foreach ($variable in $signingVariables) { [Environment]::SetEnvironmentVariable($variable, $oldEnvironment[$variable], 'Process') }
    $securePassword = $null
    $credential = $null
}
