# Notification System with AWS SQS Integration

## 📖 About This Project

This is a **production-ready email notification system** built with Spring Boot that demonstrates how to build a scalable, asynchronous notification service using AWS SQS as a message queue.

### What It Does

1. **Accepts notification requests** via REST API
2. **Saves jobs to PostgreSQL** for audit trail
3. **Pushes messages to AWS SQS** for asynchronous processing
4. **Polls SQS queue** every 10 seconds to fetch pending notifications
5. **Sends emails via Gmail SMTP** with rate limiting and retry logic

### Why This Architecture?

- **Decoupled:** API responds immediately without waiting for email to be sent
- **Scalable:** Multiple instances can process messages from the same queue
- **Reliable:** Messages persist in SQS even if the app crashes
- **Auditable:** All jobs are tracked in PostgreSQL database
- **Production-Ready:** Includes rate limiting, retries, and comprehensive logging

### Tech Stack

- **Backend:** Spring Boot 3.5.7, Java 21
- **Database:** PostgreSQL 15 (audit trail)
- **Cache:** Redis 7 (optional)
- **Message Queue:** AWS SQS
- **Email:** Gmail SMTP
- **Build Tool:** Maven

---

## 🏗️ Architecture

### Simple Flow

```
Client → REST API → Save to DB → Push to SQS → Scheduler Polls → Send Email
```

### Detailed Architecture

```
┌──────────────┐
│   Client     │
└──────┬───────┘
       │ POST /api/notifications
       ▼
┌──────────────────────────────────────────────────────────┐
│  NotificationController                                  │
│  • Receives notification request                        │
│  • Returns job ID immediately                           │
└──────┬───────────────────────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────────────────────┐
│  NotificationService                                     │
│  1. Save job to PostgreSQL (status: QUEUED)             │
│  2. Push message to AWS SQS                             │
└──────┬───────────────────────────────────────────────────┘
       │
       ├─────────────────┬─────────────────┐
       ▼                 ▼                 ▼
┌─────────────┐   ┌─────────────┐   ┌─────────────┐
│ PostgreSQL  │   │   AWS SQS   │   │   Redis     │
│ (Audit DB)  │   │   (Queue)   │   │  (Cache)    │
└─────────────┘   └──────┬──────┘   └─────────────┘
                         │
                         │ Poll every 10s
                         ▼
                  ┌──────────────────┐
                  │ SqsScheduler     │
                  │ • Fetch messages │
                  │ • Parse JSON     │
                  │ • Delete message │
                  └────────┬─────────┘
                           │
                           ▼
                  ┌──────────────────┐
                  │ NotificationSender│
                  │ • Rate limiting  │
                  │ • Auto retry     │
                  │ • Send email     │
                  └────────┬─────────┘
                           │
                           ▼
                  ┌──────────────────┐
                  │   Gmail SMTP     │
                  └──────────────────┘
```

## 📦 Components

### 1. **NotificationController**
- REST API endpoint: `POST /api/notifications`
- Accepts notification requests
- Returns job IDs

### 2. **NotificationService**
- Creates notification jobs
- Saves to PostgreSQL (audit trail)
- Pushes messages to SQS queue

### 3. **SqsNotificationScheduler**
- Polls SQS queue every 10 seconds
- Fetches up to 10 messages per poll
- Processes messages asynchronously
- Only active when `aws.sqs.enabled=true`

### 4. **NotificationSender**
- Sends emails via Gmail SMTP
- Rate limiting (3 concurrent, 2s delay)
- Automatic retry (up to 3 attempts)

### 5. **PostgreSQL Database**
- Stores all notification jobs
- Provides audit trail
- Tracks job status

### 6. **AWS SQS Queue**
- Message queue for notifications
- Decouples API from processing
- Enables horizontal scaling

## 🚀 Getting Started

### Prerequisites

- Java 21
- Maven 3.6+
- PostgreSQL 15+
- Redis 7+
- AWS Account (for SQS)
- Gmail account with App Password

### 1. Setup PostgreSQL

```bash
# Start PostgreSQL
docker run -d \
  --name postgres \
  -e POSTGRES_PASSWORD=1234 \
  -p 5432:5432 \
  postgres:15
```

### 2. Setup Redis

```bash
# Start Redis
docker run -d \
  --name redis \
  -p 6379:6379 \
  redis:7-alpine
```

### 3. Setup AWS SQS

#### Option A: Using AWS Console

1. Go to [AWS SQS Console](https://console.aws.amazon.com/sqs/)
2. Click "Create queue"
3. **Queue name:** `notification-queue`
4. **Type:** Standard Queue
5. **Configuration:**
   - Visibility timeout: 30 seconds
   - Message retention: 4 days
   - Receive message wait time: 10 seconds (long polling)
6. Click "Create queue"

#### Option B: Using AWS CLI

```bash
# Create SQS queue
aws sqs create-queue \
  --queue-name notification-queue \
  --region us-east-1 \
  --attributes '{
    "VisibilityTimeout": "30",
    "MessageRetentionPeriod": "345600",
    "ReceiveMessageWaitTimeSeconds": "10"
  }'
```

### 4. Configure Application

Update `src/main/resources/application.yml`:

```yaml
# AWS Configuration
aws:
  region: us-east-1
  accessKeyId: ${AWS_ACCESS_KEY_ID}
  secretAccessKey: ${AWS_SECRET_ACCESS_KEY}
  sqs:
    enabled: true
    queue:
      notification: notification-queue

# Email Configuration
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: your-email@gmail.com
    password: your-app-password
```

**Or use environment variables:**

```bash
export AWS_REGION=us-east-1
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
```

### 5. Build and Run

```bash
# Build
mvn clean package

# Run
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

## 📝 API Usage

### Create Notification

```bash
curl -X POST http://localhost:8080/api/notifications \
  -H "Content-Type: application/json" \
  -d '[{
    "userId": 1,
    "type": "EMAIL",
    "templateKey": "welcome",
    "userName": "John Doe",
    "recipientEmail": "john@example.com",
    "sendAt": "2025-11-03T12:00:00",
    "data": {
      "message": "Welcome to our platform!"
    }
  }]'
```

### Response

```json
[
  {
    "jobId": 1,
    "status": "QUEUED",
    "message": "Notification job created successfully"
  }
]
```

## ⚙️ Configuration

### Scheduler Settings

```yaml
app:
  scheduler:
    sqs-poll-interval-seconds: 10  # Poll SQS every 10 seconds
    sqs-max-messages: 10           # Fetch up to 10 messages per poll
```

### Email Rate Limiting

```yaml
app:
  email:
    rate-limit:
      enabled: true              # Enable rate limiting
      delay-ms: 2000             # 2 seconds between emails
      max-concurrent: 3          # Max 3 concurrent emails
      timeout-seconds: 300       # 5 minute timeout
    retry:
      enabled: true              # Enable retry
      max-attempts: 3            # Retry up to 3 times
      delay-ms: 5000             # 5 seconds between retries
```

### Database Connection Pool

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 30        # Max 30 connections
      minimum-idle: 10             # Keep 10 ready
      connection-timeout: 60000    # 60 second timeout
      leak-detection-threshold: 60000  # Warn if held > 60s
```

## 🔍 Monitoring

### Application Logs

```
INFO  ... ✅ Job 1 sent to SQS successfully
INFO  ... 📨 [SQS Scheduler] Received 5 messages from queue
INFO  ... 🔄 [SQS Scheduler] Processing job ID=1
INFO  ... ✅ [SQS Scheduler] Successfully processed job 1
```

### Job Status

Jobs can have the following statuses:
- `QUEUED` - Saved to DB and sent to SQS
- `SENDING` - Currently being processed
- `COMPLETED` - Successfully sent
- `FAILED` - Failed after retries

### AWS CloudWatch

Monitor SQS metrics:
- Messages sent
- Messages received
- Messages in queue
- Message age

## 🧪 Testing

### Test with Single Notification

```bash
curl -X POST http://localhost:8080/api/notifications \
  -H "Content-Type: application/json" \
  -d '[{
    "userId": 1,
    "type": "EMAIL",
    "templateKey": "welcome",
    "userName": "Test User",
    "recipientEmail": "test@example.com",
    "sendAt": "2025-11-03T12:00:00",
    "data": {}
  }]'
```

### Test with Multiple Notifications

```bash
# Send 50 notifications
for i in {1..50}; do
  curl -X POST http://localhost:8080/api/notifications \
    -H "Content-Type: application/json" \
    -d '[{
      "userId": '$i',
      "type": "EMAIL",
      "templateKey": "welcome",
      "userName": "User '$i'",
      "recipientEmail": "user'$i'@example.com",
      "sendAt": "2025-11-03T12:00:00",
      "data": {}
    }]'
done
```

## 📊 Performance

### Throughput

- **API Response:** < 100ms (saves to DB + pushes to SQS)
- **Processing Latency:** 10-20 seconds (scheduler poll interval)
- **Email Sending:** ~20-30 emails/minute (Gmail rate limit)

### Scalability

- **Horizontal Scaling:** Multiple instances can poll same SQS queue
- **Queue Capacity:** Unlimited (SQS handles millions of messages)
- **Database:** Connection pool supports 30 concurrent operations

## 🔧 Troubleshooting

### Issue: SQS not receiving messages

**Check:**
1. `aws.sqs.enabled=true` in application.yml
2. AWS credentials are valid
3. Queue name is correct
4. Region is correct

### Issue: Messages not being processed

**Check:**
1. Scheduler is running (look for log: "📊 [SQS Scheduler] Polling queue")
2. Messages are in the queue (check AWS Console)
3. No errors in application logs

### Issue: Email sending fails

**Check:**
1. Gmail credentials are correct
2. Using App Password (not regular password)
3. Rate limiting is enabled
4. Check Gmail SMTP logs

## 💰 Cost Estimate

### AWS SQS Pricing

- **Free Tier:** 1 million requests/month
- **After Free Tier:** $0.40 per million requests

### Example Usage

**100 emails/day:**
```
100 × 30 days = 3,000 emails/month
3,000 × 2 requests (send + receive) = 6,000 requests
Cost: $0 (within free tier)
```

**10,000 emails/day:**
```
10,000 × 30 days = 300,000 emails/month
300,000 × 2 requests = 600,000 requests
Cost: $0 (within free tier)
```

## 📚 Project Structure

```
src/main/java/com/scheduler/demo/
├── config/
│   ├── AsyncConfig.java              # Thread pool configuration
│   ├── RedisConfig.java              # Redis cache configuration
│   └── SqsConfig.java                # AWS SQS configuration
├── controller/
│   └── NotificationController.java   # REST API endpoints
├── dto/
│   ├── CreateNotificationRequest.java
│   ├── NotificationMessage.java      # SQS message format
│   └── NotificationResponse.java
├── model/
│   ├── NotificationJob.java          # Database entity
│   └── Template.java
├── repository/
│   ├── NotificationJobRepository.java
│   └── TemplateRepository.java
├── scheduler/
│   └── SqsNotificationScheduler.java # SQS polling scheduler
└── service/
    ├── NotificationSender.java       # Email sending logic
    ├── NotificationService.java      # Business logic
    ├── SqsNotificationService.java   # SQS operations
    └── TemplateService.java
```

## 🎯 Key Features

✅ **Asynchronous Processing** - API responds immediately, processing happens in background  
✅ **Scalable** - Horizontal scaling with multiple instances  
✅ **Reliable** - PostgreSQL audit trail + SQS message persistence  
✅ **Rate Limited** - Prevents Gmail blocking  
✅ **Auto Retry** - Automatic retry on temporary failures  
✅ **Monitoring** - Comprehensive logging and CloudWatch metrics

