param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$Count = 5,
    [switch]$IncludeDuplicate
)

$sampleUrls = @(
    "https://placehold.co/1200x800/png?text=realteeth-01",
    "https://placehold.co/1200x800/png?text=realteeth-02",
    "https://placehold.co/1200x800/png?text=realteeth-03",
    "https://placehold.co/1200x800/png?text=realteeth-04",
    "https://placehold.co/1200x800/png?text=realteeth-05",
    "https://placehold.co/1200x800/png?text=realteeth-06"
)

$requestUrls = $sampleUrls | Select-Object -First ([Math]::Min($Count, $sampleUrls.Count))
if ($IncludeDuplicate -and $requestUrls.Count -gt 0) {
    $requestUrls += $requestUrls[0]
}

$results = foreach ($imageUrl in $requestUrls) {
    $payload = @{ imageUrl = $imageUrl } | ConvertTo-Json -Compress

    try {
        $response = Invoke-RestMethod `
            -Method Post `
            -Uri "$BaseUrl/api/tasks" `
            -ContentType "application/json" `
            -Body $payload

        [pscustomobject]@{
            imageUrl = $imageUrl
            id       = $response.id
            status   = $response.status
            created  = $response.created
            message  = $response.message
        }
    } catch {
        throw "Failed to create task for '$imageUrl': $($_.Exception.Message)"
    }
}

$results | Format-Table -AutoSize
