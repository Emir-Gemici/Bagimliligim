param(
    [string[]]$Tasks = @(':app:bundleRelease', ':app:assembleRelease', ':app:verifyPrivacyManifests'),
    [string]$PublicConfiguration = '.local/backend.public.json'
)

$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$configurationPath = if ([System.IO.Path]::IsPathRooted($PublicConfiguration)) {
    $PublicConfiguration
} else { Join-Path $projectRoot $PublicConfiguration }
$configuration = Get-Content -LiteralPath $configurationPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($configuration.url -notmatch '^https://[a-z0-9-]+\.supabase\.co$') {
    throw 'A valid HTTPS Supabase project URL is required.'
}
if ($configuration.publishableKey -notmatch '^sb_publishable_[A-Za-z0-9_-]{20,}$') {
    throw 'Use a publishable client key. Secret and service-role keys are not accepted.'
}
$previousUrl = $env:BAGIMLILIGIM_BACKEND_URL
$previousPublicKey = $env:BAGIMLILIGIM_PUBLIC_KEY
if ($configuration.googleWebClientId -notmatch '^[0-9]+-[a-z0-9]+\.apps\.googleusercontent\.com$') {
    throw 'Configure googleWebClientId with the public Web OAuth client ID before building the connected release.'
}
$previousGoogleClient = $env:BAGIMLILIGIM_GOOGLE_WEB_CLIENT_ID
try {
    $env:BAGIMLILIGIM_BACKEND_URL = $configuration.url
    $env:BAGIMLILIGIM_PUBLIC_KEY = $configuration.publishableKey
    $env:BAGIMLILIGIM_GOOGLE_WEB_CLIENT_ID = $configuration.googleWebClientId
    & (Join-Path $PSScriptRoot 'build-release-windows.ps1') -Tasks $Tasks
} finally {
    [Environment]::SetEnvironmentVariable('BAGIMLILIGIM_BACKEND_URL', $previousUrl, 'Process')
    [Environment]::SetEnvironmentVariable('BAGIMLILIGIM_PUBLIC_KEY', $previousPublicKey, 'Process')
    [Environment]::SetEnvironmentVariable('BAGIMLILIGIM_GOOGLE_WEB_CLIENT_ID', $previousGoogleClient, 'Process')
}
