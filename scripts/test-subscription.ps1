# Test the exact KidsTubeTV subscription downloader from a trusted local PC.
# Never commit a real provider token, append it to the Gradle command, or echo it.
# Run at the repository root with installed Android SDK, JDK 17 and Gradle.
$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..'))
$secureInput = Read-Host '请输入 HTTPS 订阅链接（输入隐藏，不记录到命令历史）' -AsSecureString
$handle = [IntPtr]::Zero
$exitCode = 1
try {
    $handle = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureInput)
    $url = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($handle)
    if ([string]::IsNullOrWhiteSpace($url)) {
        throw '订阅链接为空，未发起任何网络请求。'
    }
    # Environment variables are inherited by Gradle's test JVM; unlike flags
    # they are not echoed in process command lines or source repositories.
    $env:KIDSTUBE_TEST_SUBSCRIPTION_URL = $url
    $url = $null

    # The helper is PowerShell: it throws on any download/checksum failure.
    & (Join-Path $PSScriptRoot 'fetch-mihomo.ps1')

    # Gradle is run without --info/--debug to avoid printing environment details.
    & gradle --no-daemon :app:testDebugUnitTest --tests 'dev.kidstubetv.data.SubscriptionLiveTest'
    $exitCode = $LASTEXITCODE
} finally {
    Remove-Item Env:KIDSTUBE_TEST_SUBSCRIPTION_URL -ErrorAction SilentlyContinue
    if ($handle -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($handle)
    }
}
exit $exitCode
