Based on the provided code snippets from your Clojure User Management API project, here's a suggested `README.md` file:

---

# User API

## Overview

**User API** is a Clojure-based web service designed for managing users within an application. It provides endpoints for user creation, authentication, retrieval, updating, and deletion, with role-based access control to ensure secure operations.

## Features

- **User Management**: Create, retrieve, update, and delete users.
- **Authentication**: Secure login functionality using JWT (JSON Web Tokens).
- **Authorization**: Role-based access control (admin and user roles).
- **Input Validation**: Ensures data integrity for user-related operations.
- **Testing**: Comprehensive test suite using `clojure.test` and `ring.mock.request`.

## Getting Started

### Prerequisites

- **Leiningen**: Ensure you have [Leiningen](https://leiningen.org/) installed for managing Clojure projects.
- **Java**: Java 8 or higher is required.

### Installation

1. **Clone the repository**:

   ```bash
   git clone https://github.com/yourusername/user-api.git
   cd user-api
   ```

2. **Install Dependencies**:

   The project dependencies are managed via the `project.clj` file. Ensure you have the necessary dependencies by running:

   ```bash
   lein deps
   ```

### Running the Application

To start the server, use the following command:

```bash
lein run
```

The server will start on the port specified in `core.clj` within the `config` map (default is `3000`).

### Development

The project utilizes [Calva](https://calva.io/) for Clojure development within VSCode. To connect to the REPL, use the configuration provided in `repl.calva-repl`.

### Configuration

The application configurations are externalized in the `core.clj` file under the `config` map. You can modify settings such as server port and JWT secret key there.

```clojure
(def config
  {:server {:port 3000}
   :api {:version "1.0"}
   :jwt {:secret "your-256-bit-secret"}})
```

**Note**: For production, ensure to set a secure JWT secret.

## API Endpoints

### Authentication

- **POST /login**

  Authenticate a user and receive a JWT.

  **Request Body**:

  ```json
  {
    "email": "user@example.com",
    "password": "password123"
  }
  ```

  **Response**:

  - `200 OK`: Returns a JWT token.

    ```json
    {
      "token": "<jwt_token_here>"
    }
    ```

  - `401 Unauthorized`: Invalid credentials.

### User Management

- **POST /users**

  Create a new user.

  **Request Body**:

  ```json
  {
    "name": "Alice",
    "email": "alice@example.com",
    "password": "password123",
    "role": "admin" // Optional; defaults to "user"
  }
  ```

  **Responses**:

  - `201 Created`: User successfully created.

    ```json
    {
      "id": "generated-uuid",
      "name": "Alice",
      "email": "alice@example.com",
      "role": "admin"
    }
    ```

  - `400 Bad Request`: Invalid data or email already exists.

- **GET /users**

  Retrieve a list of all users. **Admin only**.

  **Headers**:

  ```http
  Authorization: Bearer <jwt_token>
  ```

  **Response**:

  - `200 OK`: Returns a list of users excluding passwords.

- **GET /users/:id**

  Retrieve a specific user by ID.

  **Headers**:

  ```http
  Authorization: Bearer <jwt_token>
  ```

  **Response**:

  - `200 OK`: User details excluding password.
  - `404 Not Found`: User not found.

- **PUT /users/:id**

  Update a user's information.

  **Headers**:

  ```http
  Authorization: Bearer <jwt_token>
  ```

  **Request Body**:

  ```json
  {
    "name": "New Name",
    "email": "newemail@example.com",
    "password": "newpassword123"
  }
  ```

  **Response**:

  - `200 OK`: Updated user details.
  - `400 Bad Request`: Invalid data.
  - `404 Not Found`: User not found.

- **DELETE /users/:id**

  Delete a user. **Admin only**.

  **Headers**:

  ```http
  Authorization: Bearer <jwt_token>
  ```

  **Response**:

  - `204 No Content`: User successfully deleted.
  - `403 Forbidden`: Insufficient permissions.
  - `404 Not Found`: User not found.

### Root

- **GET /**

  Welcome message with available endpoints.

  **Response**:

  - `200 OK`: HTML content listing available API endpoints.

## Testing

The project includes a suite of tests located in `core_test.clj`. Tests cover user creation, authentication, authorization, and edge cases.

### Running Tests

Execute the tests using Leiningen:

```bash
lein test
```

## Project Structure

- **project.clj**: Project configuration and dependencies.
- **core.clj**: Main application logic, including routes, handlers, middleware, and server startup.
- **core_test.clj**: Test suite for the application.
- **repl.calva-repl**: Configuration log for Calva REPL setup.

## Dependencies

Key dependencies are listed in `project.clj`:

- **Clojure 1.12.0**
- **Ring**: HTTP server and middleware.
- **Reitit**: Routing library.
- **Muuntaja**: Format negotiation.
- **Buddy**: Authentication and authorization.
- **Cheshire**: JSON encoding/decoding.
- **Lein-Cloverage**: Code coverage analysis.

## Additional Information

For more details on configuring and extending the application, refer to the code comments within `core.clj` and `core_test.clj`. Ensure to handle sensitive information, like JWT secrets, securely in a production environment.

---

If you need further customization or additional sections in the `README.md`, feel free to provide more details or specific requirements!