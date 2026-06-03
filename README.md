# User Service

Handles user registration, authentication (email/password, OTP, OAuth2), JWT issuance, and profile management.

## Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | /api/v1/auth/register | Public | Register new user |
| POST | /api/v1/auth/login | Public | Login with email/password |
| POST | /api/v1/auth/otp/send | Public | Send OTP to phone |
| POST | /api/v1/auth/otp/verify | Public | Verify OTP |
| POST | /api/v1/auth/refresh | Public | Refresh access token |
| GET | /api/v1/users/me | JWT | Get current user |
| GET | /api/v1/users/{id} | JWT | Get user by ID |
| PUT | /api/v1/users/{id} | JWT | Update user |
| DELETE | /api/v1/users/{id} | JWT/ADMIN | Delete user |

## Kafka Topics Published
- `notification.send` — Welcome email, OTP, login notifications

## Tech Stack
- Spring Boot 4.0.5
- Spring Security 6 + JWT (JJWT)
- PostgreSQL + Flyway
- Redis (OTP storage, session caching)
- Kafka
