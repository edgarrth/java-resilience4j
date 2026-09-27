$ErrorActionPreference = "Stop"
$PspUrl = if ($env:PSP_URL) { $env:PSP_URL } else { "http://localhost:8081" }
$ScenarioFile = Join-Path $PSScriptRoot "scenarios.json"

# Cargo los escenarios y dejo el simulador en un estado conocido para repetir las pruebas.
Invoke-RestMethod -Method Post -Uri "$PspUrl/admin/scenarios/import" -ContentType "application/json" -InFile $ScenarioFile | Out-Null
Invoke-RestMethod -Method Post -Uri "$PspUrl/admin/scenarios/healthy/activate" | Out-Null
Invoke-RestMethod -Method Post -Uri "$PspUrl/admin/reset" | Out-Null
Write-Host "Escenarios cargados. El PSP quedó en modo healthy y con contadores limpios."
