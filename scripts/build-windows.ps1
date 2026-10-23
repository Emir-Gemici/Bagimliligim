param(
    [string[]]$Tasks = @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug')
)

$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$previousLocation = Get-Location
$mappedDrive = $null
$previousJava = $env:JAVA_HOME
$exitStatus = 1

try {
    if (-not $env:JAVA_HOME) {
        $studioRuntime = 'C:\Program Files\Android\Android Studio\jbr'
        if (Test-Path -LiteralPath (Join-Path $studioRuntime 'bin\java.exe')) {
            $env:JAVA_HOME = $studioRuntime
        }
    }
    if ($projectRoot -match '[^\x00-\x7F]') {
        foreach ($letter in 'S','T','U','V','W','X','Y','Z') {
            $candidate = $letter + ':'
            if (-not (Test-Path ($candidate + '\'))) {
                & subst.exe $candidate $projectRoot
                if ($LASTEXITCODE -ne 0) { throw 'Temporary ASCII workspace mapping failed.' }
                $mappedDrive = $candidate
                break
            }
        }
        if (-not $mappedDrive) { throw 'No free drive letter is available for the temporary workspace mapping.' }
        Set-Location ($mappedDrive + '\')
    } else {
        Set-Location -LiteralPath $projectRoot
    }
    & .\gradlew.bat @Tasks --console=plain
    $exitStatus = $LASTEXITCODE
} finally {
    Set-Location -LiteralPath $previousLocation.Path
    if ($mappedDrive) { & subst.exe $mappedDrive /D }
    $env:JAVA_HOME = $previousJava
}
exit $exitStatus
