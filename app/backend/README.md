# Cameraman Photo Studio - Enterprise Backend

## Technology Stack
- **Java 21** + **Spring Boot 3.5**
- **PostgreSQL 16** — primary database
- **Redis 7** — caching & session management
- **Cloudflare R2 / AWS S3** — photo storage
- **Razorpay** — payment gateway
- **WhatsApp Business API** — messaging
- **Docker + Nginx** — deployment

---

## Modules
| Module | Endpoints prefix | Description |
|---|---|---|
| Auth | `/api/auth` | Login, OTP, JWT, refresh |
| Users | `/api/users` | User CRUD, profile |
| Roles | `/api/roles` | RBAC roles & permissions |
| Bookings | `/api/bookings` | Full booking lifecycle |
| Packages | `/api/packages` | Photography packages |
| Gallery | `/api/galleries` | Gallery + photo upload |
| Photo Selection | `/api/selections` | Client selection portal |
| Albums | `/api/albums` | Album workflow |
| Inventory | `/api/inventory` | Stock management |
| Billing | `/api/billing/invoices` | GST invoicing |
| Payments | `/api/payments` | Razorpay + manual |
| Dashboard | `/api/dashboard` | Metrics & analytics |
| Reports | `/api/reports` | Excel exports |
| CMS | `/api/cms` | Website content |
| Settings | `/api/settings` | Studio branding |

---

## Quick Start (Development)

### Prerequisites
- Java 21
- Docker & Docker Compose
- Maven 3.9+

### 1. Start dependencies
```bash
docker compose up postgres redis -d
```

### 2. Configure environment
```bash
cp .env.example .env
# Edit .env with your values
```

### 3. Run application
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### 4. Access Swagger UI
```
http://localhost:8080/api/swagger-ui.html
```

### Default Admin Credentials
- **Email:** `admin@cameraman.com`
- **Password:** `Admin@2024`

---

## Production Deployment

### Full Stack with Docker Compose
```bash
# Set all env variables in .env
docker compose up -d

# View logs
docker compose logs -f backend
```

### SSL Setup
Place SSL certificates in `nginx/ssl/`:
- `fullchain.pem`
- `privkey.pem`

Or use Let's Encrypt:
```bash
certbot certonly --standalone -d cameraman.com -d www.cameraman.com
```

### Database Migrations
Flyway runs automatically on startup.
Manual migration:
```bash
./mvnw flyway:migrate -Dflyway.url=jdbc:postgresql://host/db -Dflyway.user=user -Dflyway.password=pass
```

---

## API Authentication

All protected endpoints require `Authorization: Bearer <token>`.

### Login
```bash
POST /api/auth/login
{
  "identifier": "admin@cameraman.com",
  "password": "Admin@2024"
}
```

### Refresh Token
```bash
POST /api/auth/refresh
{ "refreshToken": "..." }
```

---

## Storage Configuration

### Cloudflare R2
```yaml
storage:
  type: s3
  s3:
    endpoint: https://{account-id}.r2.cloudflarestorage.com
    bucket: cameraman-photos
    region: auto
    access-key: your_key
    secret-key: your_secret
```

### AWS S3
```yaml
storage:
  s3:
    endpoint: https://s3.amazonaws.com
    region: ap-south-1
    bucket: cameraman-photos
```

---

## Key Features

### Photo Upload
- Chunked/multipart upload supported
- Background async processing (thumbnail, preview, watermark)
- Signed URLs with configurable expiry
- WebP conversion ready

### Billing
- GST-compliant A4/A6 PDF invoices
- Auto-send via email & WhatsApp
- CGST/SGST/IGST calculation
- Advance, partial, full payment support

### Security
- JWT with refresh token rotation
- Token blacklisting in Redis
- Brute force protection + account lockout
- BCrypt password hashing (cost=12)
- Rate limiting per IP (Bucket4j)

---

## Monitoring

| Endpoint | Description |
|---|---|
| `/api/actuator/health` | Health check |
| `/api/actuator/metrics` | App metrics |
| `/api/actuator/prometheus` | Prometheus scrape |

---

## Support
For issues contact: dev@cameraman.com
