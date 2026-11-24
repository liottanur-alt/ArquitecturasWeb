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
function Get-ValorEntorno([string]$Nombre) {
    $valor = [Environment]::GetEnvironmentVariable($Nombre)
    if (-not [string]::IsNullOrWhiteSpace($valor)) { return $valor }
    $archivoEnv = Join-Path $PSScriptRoot '../../.env'
    if (Test-Path -LiteralPath $archivoEnv) {
        $linea = @(Get-Content -LiteralPath $archivoEnv | Where-Object { $_ -match "^\s*$Nombre\s*=" } | Select-Object -Last 1)
        if ($linea.Count -eq 1) {
            return ($linea[0] -replace "^\s*$Nombre\s*=", '').Trim().Trim('"', "'")
        }
    }
    throw "Definir $Nombre en el entorno o en .env antes de ejecutar las pruebas."
}
function Get-Token([string]$Email, [string]$Password) {
    $respuesta = Invoke-Api POST '/auth/login' @{ email = $Email; password = $Password } @{} @(200)
    if ([string]::IsNullOrWhiteSpace($respuesta.accessToken)) { throw 'El login no devolvio accessToken.' }
    return $respuesta.accessToken
}
function Headers-ConToken([string]$Token) {
    return @{ Authorization = "Bearer $Token" }
}
function Save-Report($Report, [string]$Path) {
    New-Item -ItemType Directory -Force (Split-Path -Parent $Path) | Out-Null
    $Report | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $Path -Encoding utf8
}
