#requires -Version 7.0
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
function Invoke-Api {
    param([string]$Method, [string]$Path, $Body = $null, [hashtable]$Headers = @{}, [int[]]$Expected = @(200))
    $argsApi = @{ Uri = "$BaseUrl$Path"; Method = $Method; Headers = $Headers; SkipHttpErrorCheck = $true; TimeoutSec = 30 }
    if ($null -ne $Body) {
        $argsApi.ContentType = 'application/json; charset=utf-8'
        $argsApi.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }
    Write-Verbose "$Method $Path"
    $response = Invoke-WebRequest @argsApi
    if ([int]$response.StatusCode -notin $Expected) {
        throw "$Method $Path : esperado $Expected, recibido $($response.StatusCode): $($response.Content)"
    }
    if ($response.Content) { return ($response.Content | ConvertFrom-Json) }
}
function Assert-Equal($Actual, $Expected, [string]$Message) {
    if ($Actual -ne $Expected) { throw "$Message : esperado '$Expected', recibido '$Actual'" }
}
function Save-Report($Report, [string]$Path) {
    New-Item -ItemType Directory -Force (Split-Path -Parent $Path) | Out-Null
    $Report | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $Path -Encoding utf8
}
