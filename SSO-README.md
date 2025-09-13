# CF-MCP-Client with SSO Integration

This is the CF-MCP-Client application with added SSO (Single Sign-On) integration, including a landing page and authentication system similar to the Spring AI Multilingual Document Analyzer.

## Features Added

- **SSO Integration**: Support for GitHub OAuth2 and Cloud Foundry SSO
- **Landing Page**: Beautiful login page with multilingual support (English/Hebrew)
- **Authentication**: Secure authentication flow with session management
- **User Interface**: Modern, responsive design with gradient backgrounds
- **Provider Detection**: Automatic detection of authentication provider (GitHub vs CF-SSO)

## Setup Instructions

### 1. Prerequisites

- Java 21+
- Maven 3.6+
- PostgreSQL database
- GitHub OAuth App (for GitHub authentication)

### 2. GitHub OAuth Setup

1. Go to GitHub Settings > Developer settings > OAuth Apps
2. Create a new OAuth App with:
   - **Application name**: CF-MCP-Client
   - **Homepage URL**: `http://localhost:8080`
   - **Authorization callback URL**: `http://localhost:8080/login/oauth2/code/github`
3. Copy the Client ID and Client Secret

### 3. Environment Configuration

Set the following environment variables:

```bash
export GITHUB_CLIENT_ID=your-github-client-id
export GITHUB_CLIENT_SECRET=your-github-client-secret
```

Or create a `.env` file:

```bash
GITHUB_CLIENT_ID=your-github-client-id
GITHUB_CLIENT_SECRET=your-github-client-secret
```

### 4. Database Setup

Make sure PostgreSQL is running and create a database:

```sql
CREATE DATABASE postgres;
```

The application will automatically create the necessary tables for session management.

### 5. Running the Application

```bash
# Build the application
mvn clean package

# Run the application
java -jar target/cf-mcp-client-1.5.1.jar
```

Or run directly with Maven:

```bash
mvn spring-boot:run
```

### 6. Accessing the Application

1. Open your browser and go to `http://localhost:8080`
2. You'll be redirected to the login page
3. Click "Sign in" to authenticate with GitHub
4. After successful authentication, you'll see the main application page

## Authentication Flow

1. **Unauthenticated users** are redirected to `/login.html`
2. **Login page** shows provider-specific icons (GitHub or CF-SSO)
3. **OAuth2 flow** handles the authentication
4. **Success redirect** takes users to the main application
5. **Session management** maintains authentication state

## Security Configuration

The application uses Spring Security with OAuth2 client configuration:

- **Protected endpoints**: `/api/**`, `/chat/**`, `/document/**`, `/memory/**`, `/mcp/**`, `/prompt/**`, `/vectorstore/**`, `/metrics/**`
- **Public endpoints**: `/login`, `/login.html`, `/static/**`, `/auth/status`, `/auth/provider`, `/oauth2/**`
- **Session timeout**: 1440 minutes (24 hours)
- **CSRF protection**: Disabled for API endpoints

## Cloud Foundry Deployment

For Cloud Foundry deployment with SSO:

1. The application automatically detects CF environment via `VCAP_SERVICES`
2. Provider detection switches from GitHub to CF-SSO
3. Configure CF-SSO service binding in your manifest.yml
4. Deploy using standard CF commands

## File Structure

```
src/main/
├── java/org/tanzu/mcpclient/
│   ├── security/
│   │   ├── SecurityConfig.java      # OAuth2 security configuration
│   │   └── AuthController.java       # Authentication status endpoints
│   └── web/
│       └── WebController.java       # Web routing controller
└── resources/
    ├── static/
    │   ├── login.html               # Login page
    │   ├── index.html               # Main application page
    │   ├── favicon.ico              # Application icon
    │   └── images/                  # Static images
    └── application.yaml             # Application configuration
```

## Troubleshooting

### Common Issues

1. **Authentication fails**: Check GitHub OAuth app configuration and environment variables
2. **Database connection**: Ensure PostgreSQL is running and accessible
3. **Port conflicts**: Change server port in application.yaml if needed
4. **CORS issues**: Check WebConfiguration for CORS settings

### Logs

Check application logs for detailed error information:

```bash
# Enable debug logging
export SPRING_PROFILES_ACTIVE=debug
```

## Contributing

1. Create a feature branch: `git checkout -b feature/your-feature`
2. Make your changes
3. Test thoroughly
4. Submit a pull request

## License

This project is part of the VMware Tanzu Platform ecosystem.
