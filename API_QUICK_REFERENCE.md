# API Quick Reference

Replace `{BASE_URL}` with your ngrok URL from GitHub Actions.

## 🔍 Health & Status

```bash
# Check if app is running
curl {BASE_URL}/actuator/health
```

## 💱 Exchange Rates API

```bash
# Get all exchange rates (USD base)
curl {BASE_URL}/api/rates

# Get specific currency rate
curl {BASE_URL}/api/rates/EUR
curl {BASE_URL}/api/rates/GBP
curl {BASE_URL}/api/rates/INR
```

**Response Example:**
```json
{
  "id": 1,
  "baseCurrency": "USD",
  "targetCurrency": "EUR",
  "rate": 0.92,
  "timestamp": "2025-11-02T12:30:00"
}
```

## 📧 Notifications API

### Create Notification

```bash
curl -X POST {BASE_URL}/api/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "type": "EMAIL",
    "templateKey": "welcome",
    "sendAt": "2025-11-02T18:00:00",
    "data": {
      "userName": "John Doe",
      "orderId": "12345"
    }
  }'
```

**Notification Types:** `EMAIL`, `SMS`, `PUSH`

**Status Values:** `PENDING`, `SENDING`, `COMPLETED`, `FAILED`, `CANCELLED`

### Get All Notifications

```bash
curl {BASE_URL}/api/notifications
```

### Filter by Status

```bash
curl {BASE_URL}/api/notifications?status=PENDING
curl {BASE_URL}/api/notifications?status=COMPLETED
curl {BASE_URL}/api/notifications?status=FAILED
```

### Get Specific Notification

```bash
curl {BASE_URL}/api/notifications/1
```

### Cancel Notification

```bash
curl -X DELETE {BASE_URL}/api/notifications/1
```

**Response Example:**
```json
{
  "id": 1,
  "status": "PENDING",
  "sendAt": "2025-11-02T18:00:00"
}
```

## 📝 Templates API

### Get All Templates

```bash
curl {BASE_URL}/api/templates
```

### Create Template

```bash
curl -X POST {BASE_URL}/api/templates \
  -H "Content-Type: application/json" \
  -d '{
    "keyName": "welcome",
    "content": "Hello {{userName}}, welcome to our service! Your order {{orderId}} is confirmed."
  }'
```

### Get Specific Template

```bash
curl {BASE_URL}/api/templates/welcome
```

### Update Template

```bash
curl -X PUT {BASE_URL}/api/templates/welcome \
  -H "Content-Type: application/json" \
  -d '{
    "content": "Hi {{userName}}, your order {{orderId}} has been updated!"
  }'
```

### Delete Template

```bash
curl -X DELETE {BASE_URL}/api/templates/welcome
```

**Template Placeholders:**
- Use `{{variableName}}` syntax
- Variables are replaced from notification `data` field
- Example: `{{userName}}`, `{{orderId}}`, `{{amount}}`

## 🧪 Testing Scenarios

### Scenario 1: Test Immediate Notification

```bash
# Create notification to be sent in 1 minute
curl -X POST {BASE_URL}/api/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "type": "EMAIL",
    "templateKey": "test",
    "sendAt": "'$(date -u -d '+1 minute' +%Y-%m-%dT%H:%M:%S)'",
    "data": {"message": "Test"}
  }'

# Wait 2 minutes, then check status
sleep 120
curl {BASE_URL}/api/notifications
```

### Scenario 2: Test Scheduler

```bash
# The scheduler runs every 10 seconds (configurable)
# Create multiple notifications with different send times

# Send in 30 seconds
curl -X POST {BASE_URL}/api/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "type": "EMAIL",
    "templateKey": "test",
    "sendAt": "'$(date -u -d '+30 seconds' +%Y-%m-%dT%H:%M:%S)'",
    "data": {"test": "1"}
  }'

# Send in 1 minute
curl -X POST {BASE_URL}/api/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 2,
    "type": "SMS",
    "templateKey": "test",
    "sendAt": "'$(date -u -d '+1 minute' +%Y-%m-%dT%H:%M:%S)'",
    "data": {"test": "2"}
  }'

# Watch them get processed
watch -n 5 "curl -s {BASE_URL}/api/notifications | jq '.'"
```

### Scenario 3: Test Rate Updates

```bash
# The rate scheduler fetches new rates every 10 seconds
# Watch rates update in real-time

watch -n 10 "curl -s {BASE_URL}/api/rates | jq '.'"
```

### Scenario 4: Test Template System

```bash
# Create template
curl -X POST {BASE_URL}/api/templates \
  -H "Content-Type: application/json" \
  -d '{
    "keyName": "order-confirmation",
    "content": "Dear {{customerName}}, your order {{orderId}} for {{amount}} has been confirmed!"
  }'

# Use template in notification
curl -X POST {BASE_URL}/api/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "type": "EMAIL",
    "templateKey": "order-confirmation",
    "sendAt": "'$(date -u -d '+30 seconds' +%Y-%m-%dT%H:%M:%S)'",
    "data": {
      "customerName": "John Doe",
      "orderId": "ORD-12345",
      "amount": "$99.99"
    }
  }'
```

## 🔧 Using the Test Script

```bash
# Make it executable (first time only)
chmod +x test-api.sh

# Run all tests
./test-api.sh {BASE_URL}

# Example
./test-api.sh https://abc123.ngrok-free.app
```

## 📊 Monitoring

### Check Application Logs
- Go to GitHub Actions workflow run
- Click on the running job
- View real-time logs

### Check Scheduler Activity
Look for these log messages:
```
Polling for pending jobs...
Found X pending jobs.
[SIM-SEND] EMAIL to user=1 -> ...
```

### Check Rate Updates
Look for:
```
✅ Rates updated at: 2025-11-02T12:30:00
```

## 💡 Tips

1. **Time Format**: Use ISO 8601 format: `YYYY-MM-DDTHH:MM:SS`
2. **Timezone**: All times are in UTC
3. **Scheduler Interval**: Configurable via `app.scheduler.poll-interval-seconds` (default: 10)
4. **Rate Update Interval**: Configurable via `app.scheduler.rate-update-interval-seconds` (default: 10)

## 🐛 Common Issues

### "Connection refused"
- App might not be started yet, wait 1-2 minutes
- Check GitHub Actions logs

### "404 Not Found"
- Verify the endpoint URL
- Check if the resource exists (e.g., notification ID)

### "400 Bad Request"
- Check JSON syntax
- Verify required fields are present
- Check date format

### Notification not processing
- Check `sendAt` time is in the future
- Verify scheduler is running (check logs)
- Ensure status is `PENDING`

