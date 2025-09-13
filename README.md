# CF MCP Client with CF-SSO Integration

A Spring Boot application that provides an AI-powered chat client with Model Context Protocol (MCP) server integration, featuring Cloud Foundry Single Sign-On (CF-SSO) authentication.

## 🚀 Features

- **🔐 CF-SSO Authentication**: Seamless integration with Cloud Foundry Single Sign-On
- **🤖 AI Chat Interface**: Powered by Spring AI with support for multiple LLM providers
- **🔌 MCP Server Integration**: Model Context Protocol server connectivity
- **💾 Memory Management**: Persistent conversation history with vector storage
- **📄 Document Processing**: PDF document analysis and processing
- **🌐 Multilingual Support**: English and Hebrew language support
- **📱 Responsive UI**: Modern, mobile-friendly interface

## 🏗️ Architecture

### Authentication Flow
```
User → Landing Page → CF-SSO Login → Welcome Page → Chat Interface
```

### Technology Stack
- **Backend**: Spring Boot 3.5.3, Spring Security, Spring AI
- **Frontend**: Angular, HTML5, CSS3, JavaScript
- **Authentication**: OAuth2/OIDC with CF-SSO via java-cfenv
- **Database**: PostgreSQL with pgvector for embeddings
- **Deployment**: Cloud Foundry with java-buildpack

## 📋 Prerequisites

- Java 21+
- Maven 3.6+
- Node.js 18+ (for frontend build)
- PostgreSQL database
- Cloud Foundry CLI
- CF-SSO service instance

## 🛠️ Local Development Setup

### 1. Clone the Repository
```bash
git clone https://github.com/0pens0/cf-mcp-client.git
cd cf-mcp-client
git checkout feature/landing-page-sso
```

### 2. Database Setup
Create a PostgreSQL database:
```sql
CREATE DATABASE mydb;
CREATE USER myuser WITH PASSWORD 'mypassword';
GRANT ALL PRIVILEGES ON DATABASE mydb TO myuser;
```

### 3. Environment Configuration
Create `application-local.yaml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mydb
    username: myuser
    password: mypassword
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update

# Optional: GitHub OAuth2 for local development
spring:
  security:
    oauth2:
      client:
        registration:
          github:
            client-id: ${GITHUB_CLIENT_ID}
            client-secret: ${GITHUB_CLIENT_SECRET}
            scope: read:user, user:email
        provider:
          github:
            authorization-uri: https://github.com/login/oauth/authorize
            token-uri: https://github.com/login/oauth/access_token
            user-info-uri: https://api.github.com/user
            user-name-attribute: id
```

### 4. Build and Run
```bash
# Build the application
mvn clean package -DskipTests

# Run locally
java -jar target/cf-mcp-client-1.5.1.jar --spring.profiles.active=local
```

## ☁️ Cloud Foundry Deployment

### 1. Prerequisites
- CF-SSO service instance available in your Cloud Foundry space
- PostgreSQL service instance (optional, can use external database)

### 2. Service Binding
```bash
# Create CF-SSO service instance
cf create-service p-identity cf-sso-service

# Optional: Create PostgreSQL service
cf create-service postgresql-db shared postgres-service
```

### 3. Deploy Application
```bash
# Deploy to Cloud Foundry
cf push cf-mcp-client-sso -f manifest.yml
```

### 4. Bind Services
```bash
# Bind CF-SSO service
cf bind-service cf-mcp-client-sso cf-sso-service

# Optional: Bind PostgreSQL service
cf bind-service cf-mcp-client-sso postgres-service

# Restart application to pick up service bindings
cf restart cf-mcp-client-sso
```

## 🔧 Configuration

### Automatic CF-SSO Configuration
The application uses `java-cfenv-boot-pivotal-sso` for automatic OAuth2 configuration:

```xml
<dependency>
    <groupId>io.pivotal.cfenv</groupId>
    <artifactId>java-cfenv-boot-pivotal-sso</artifactId>
    <version>3.5.0</version>
</dependency>
```

This automatically configures:
- OAuth2 client registration with ID `sso`
- Authorization, token, and user info URIs
- Client ID and secret from service binding
- Proper scopes and redirect URIs

### Manual Configuration (if needed)
If automatic configuration doesn't work, you can manually configure OAuth2 in `application.yaml`:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          cf-sso:
            client-id: ${CF_SSO_CLIENT_ID}
            client-secret: ${CF_SSO_CLIENT_SECRET}
            scope: openid, profile
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
        provider:
          cf-sso:
            authorization-uri: ${CF_SSO_AUTH_URI}
            token-uri: ${CF_SSO_TOKEN_URI}
            user-info-uri: ${CF_SSO_USER_INFO_URI}
            user-name-attribute: user_name
            jwk-set-uri: ${CF_SSO_JWK_SET_URI}
```

## 🌐 Application Endpoints

### Public Endpoints
- `/` - Landing page
- `/login.html` - Login page
- `/login` - OAuth2 login redirect
- `/auth/provider` - Get active authentication provider
- `/auth/status` - Get authentication status

### Protected Endpoints
- `/welcome` - Post-login welcome page
- `/chat` - Chat interface (if implemented)
- `/document/**` - Document processing endpoints

### OAuth2 Endpoints
- `/oauth2/authorization/sso` - CF-SSO authorization
- `/oauth2/authorization/github` - GitHub authorization (fallback)
- `/login/oauth2/code/sso` - CF-SSO callback
- `/login/oauth2/code/github` - GitHub callback

## 🔍 Troubleshooting

### Common Issues

#### 1. "Invalid Client Registration with Id: cf-sso"
**Problem**: Login button uses wrong registration ID  
**Solution**: Update login button to use `/oauth2/authorization/sso` instead of `/oauth2/authorization/cf-sso`

#### 2. "Malformed Jwk set - Missing required 'keys' member"
**Problem**: CF-SSO JWK Set URI returns invalid data  
**Solution**: Use java-cfenv auto-configuration instead of manual configuration

#### 3. "Ambiguous mapping" errors
**Problem**: Multiple controllers handle the same endpoint  
**Solution**: Remove conflicting mappings or use different endpoint paths

#### 4. Application crashes on startup
**Problem**: Bean conflicts or missing dependencies  
**Solution**: Check logs for specific error messages and resolve conflicts

### Debugging
Enable debug logging:
```yaml
logging:
  level:
    org.springframework.security: DEBUG
    org.springframework.security.oauth2: DEBUG
    io.pivotal.cfenv: DEBUG
```

## 📚 Key Components

### Controllers
- **`LoginController`**: Handles OAuth2 login redirects and provider selection
- **`WelcomeController`**: Displays post-login welcome page with user info
- **`AuthController`**: Provides authentication status and provider information
- **`WebController`**: Serves static pages and handles root redirects

### Security Configuration
- **`SecurityConfig`**: Configures Spring Security with OAuth2 login
- **Auto-configuration**: Uses java-cfenv for CF-SSO integration
- **Dynamic provider selection**: Supports CF-SSO and GitHub OAuth2

### Frontend
- **`login.html`**: Login page with dynamic provider selection
- **`welcome.html`**: Thymeleaf template for welcome page
- **Angular components**: Chat interface and document processing

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/new-feature`
3. Commit changes: `git commit -am 'Add new feature'`
4. Push to branch: `git push origin feature/new-feature`
5. Create a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Spring Boot and Spring Security teams for excellent OAuth2 support
- Cloud Foundry team for CF-SSO service
- java-cfenv project for automatic service binding configuration
- Spring AI team for AI integration capabilities

## 📞 Support

For issues and questions:
- Create an issue in the GitHub repository
- Check the troubleshooting section above
- Review Cloud Foundry and CF-SSO documentation

---

**Built with ❤️ for the Cloud Foundry and Spring communities**