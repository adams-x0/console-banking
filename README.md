# Console Banking Application

A Java console application for customer registration, account applications,
employee approvals, and banking transactions.

Built as a training project using a three-tier architecture and PostgreSQL
persistence through JDBC.

## Features

### Customers

- Register and log in.
- Apply for a bank account.
- View account balances and application status.
- Deposit into and withdraw from approved accounts.
- Transfer money to another approved account.
- Confirm the recipient's name and account number before transferring.

### Employees

- View customer profiles and their accounts.
- Approve or deny pending account applications.
- View the employee and timestamp associated with an account review.

### Validation and transaction handling

- Reject nonpositive amounts and fractions of a cent.
- Prevent withdrawals and transfers that exceed the source balance.
- Enforce the maximum supported account balance.
- Verify source-account ownership.
- Require approved accounts for money operations.
- Prevent transfers to the same account.
- Prevent duplicate pending applications for a customer.
- Save balance changes and transaction records in one database transaction.
- Lock account rows during money operations.
- Acquire transfer locks in ascending account-ID order.

## Technology

- Java — the current Maven configuration targets Java 27
- Maven
- PostgreSQL, including AWS-hosted PostgreSQL-compatible databases
- JDBC and the DAO pattern
- JUnit Jupiter
- Mockito
- JaCoCo
- Passay for password validation
- Jakarta Mail for email syntax validation

## Architecture

```text
Console
   |
Controllers
   |
Services
   |
DAOs
   |
PostgreSQL
```

- **Controllers:** console menus, input, and output.
- **Services:** business validation, authorization, and error translation.
- **DAOs:** SQL queries, persistence, locking, and database transactions.
- **Entities:** representations of stored records.
- **DTOs:** data returned for display without exposing password hashes.

This project uses plain JDBC rather than JPA or Hibernate.

## Database

The schema is located at:

```text
src/main/resources/console-banking.sql
```

| Table | Purpose |
|---|---|
| `bank_users` | Customer and employee profiles, roles, and password hashes |
| `accounts` | Balances, application status, and review information |
| `account_owners` | Links users to accounts |
| `bank_transactions` | Records deposits, withdrawals, and transfers |

Foreign keys maintain relationships between the tables. Database constraints
restrict account statuses, negative balances, and invalid transaction records.

The ownership table can represent multiple owners, but the joint-account
application feature is not implemented.

## Prerequisites

- JDK 27 configured for both Java and Maven
- Maven installed and available on PATH
- A PostgreSQL database
- A terminal that supports Java's `System.console()`
- Internet access for dependency downloads and the registration password
  breach check

Check your installations:

```powershell
java -version
mvn -version
```

## Database setup

1. Create a database named `banking_db`, or choose an existing empty database.

   ```sql
   CREATE DATABASE banking_db;
   ```

2. Connect your SQL client to that database.
3. Execute `src/main/resources/console-banking.sql`.

Run the schema once against an empty schema. The script does not drop or
replace existing tables.

The application does not create the database or tables automatically.

## Environment variables

The application reads these exact variable names:

| Variable | Meaning |
|---|---|
| `DB_URL` | PostgreSQL JDBC connection URL |
| `DB_USERNAME` | Database login username |
| `DB_PASSWORD` | Database login password |

These credentials connect Java to PostgreSQL. They are separate from the
customer usernames and passwords entered in the application.

### Windows PowerShell

Run these commands in the same terminal used to launch the application:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/banking_db"
$env:DB_USERNAME = "postgres"

# Read the database password without displaying it.
$databasePassword = Read-Host "Database password" -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new(
    "",
    $databasePassword
).Password
Remove-Variable databasePassword
```

The variables apply to the current terminal session.

IntelliJ Run configuration variables do not automatically apply to commands
launched from IntelliJ's Terminal.

### AWS connection

Use your own AWS endpoint and the database containing your tables:

```text
jdbc:postgresql://YOUR_AWS_ENDPOINT:5432/banking_db?sslmode=require
```

The database must be reachable from your machine, and the database user must
have permission to use the schema, tables, and identity sequences.

`sslmode=require` encrypts the connection but does not verify the server's
identity. For certificate verification, configure `sslmode=verify-full` and
the appropriate trusted AWS CA certificate.

For password authentication, use a database user configured for password
authentication. An IAM authentication token is temporary; this application
does not automatically generate or refresh IAM tokens.

Do not commit database passwords, IAM tokens, AWS credentials, or local
environment files.

## Build and run

From the project root:

```powershell
mvn clean verify dependency:copy-dependencies "-DincludeScope=runtime"
```

Start the application on Windows:

```powershell
java -cp "target/classes;target/dependency/*" com.bank.Main
```

On macOS or Linux, after configuring the environment variables, use a colon
as the classpath separator:

```bash
java -cp "target/classes:target/dependency/*" com.bank.Main
```

Use a terminal for the interactive application. Ordinary input uses
`Scanner`, while passwords use `Console.readPassword()` to hide typed
characters. If `System.console()` is unavailable, the application prints
a message and exits.

## Create a demonstration employee

Public registration always creates a customer.

To prepare an employee for a local or demonstration database:

1. Register a dedicated account named `employee1` through the application.
2. Using an authorized database administrator connection, run:

   ```sql
   UPDATE bank_users
   SET role = 'EMPLOYEE'
   WHERE username = 'employee1'
     AND role = 'CUSTOMER'
   RETURNING user_id, username, role;
   ```

3. Log in again as `employee1` to access the employee menu.

There is no default employee password and no public employee-registration
option.

## Example workflow

1. Register a customer and log in.
2. Apply for an account.
3. Log in as an employee and approve the application.
4. Log back in as the customer.
5. Deposit `100.00`.
6. Withdraw `20.00`.
7. Transfer `30.00` to another approved account.
8. Verify the source balance is `50.00`.
9. Attempt to withdraw `60.00` and confirm that it is rejected.

The destination account must already exist and be approved.

## Tests and coverage

Run all tests:

```powershell
mvn test
```

Run the money-operation service tests:

```powershell
mvn "-Dtest=AccountMoneyServiceTest" test
```

Generate a fresh coverage report:

```powershell
mvn clean verify
```

Open:

```text
target/site/jacoco/index.html
```

The latest recorded service-layer report showed:

| Metric | Coverage |
|---|---:|
| Instruction coverage | 94% |
| Branch coverage | 98% |

These figures describe the service package, not the entire application.
Regenerate the report after code changes.

Service tests use mocked DAOs and do not require a live database or AWS
credentials. They verify validation, authorization, returned data, and
exception handling. They do not prove SQL correctness, database rollback,
or concurrency behavior.

## Password handling

- Passwords are stored as salted PBKDF2 hashes.
- Registration requires password confirmation.
- Passay checks length and character requirements.
- Registration checks for exposed passwords through Have I Been Pwned.
- Password character arrays are cleared after use where implemented.
- Response DTOs exclude password hashes.

The registration breach check requires network access. Email validation
checks syntax; it does not verify ownership of the address.

## Troubleshooting

| Problem | Check |
|---|---|
| Missing `DB_URL`, `DB_USERNAME`, or `DB_PASSWORD` | Set all three in the terminal launching Java |
| `NoClassDefFoundError` | Copy Maven dependencies again, especially after `mvn clean` |
| Application requests a terminal | Launch with the `java` command in a terminal supporting `System.console()` |
| Database connection timeout | Check endpoint, port, network routing, and AWS security settings |
| Authentication failure | Check the database username, password, and authentication mode |
| Tables not found | Execute the schema in the database specified by `DB_URL` |
| No approved accounts | Have an employee approve a pending application |
| Password registration rejection | Check password rules, breach-check result, and internet connectivity |

## Scope

This is an educational banking simulation, not a production banking system.

Joint-account applications and stored procedures are not implemented.
Transactions are recorded in the database, but there is no customer
transaction-history menu.