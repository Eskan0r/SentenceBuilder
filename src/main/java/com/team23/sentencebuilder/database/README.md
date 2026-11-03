# Connecting to the Database (AWS RDS MySQL)

`DB.java` opens a JDBC connection to our MySQL database on AWS RDS. The connection
details are read from a `.env` file in the project root using
[dotenv-java](https://github.com/cdimascio/dotenv-java).

## Prerequisites

- Java 11 or newer to run `DB.java` on its own (the full project builds with Java 21, or IntelliJ's bundled JDK)
- The project cloned and opened from the project root (the folder with `pom.xml`)
- The database credentials (ask a teammate; they are **not** in git)

## 1. Create your `.env` file

Copy the template in the project root:

```bash
cp .env.example .env
```

Fill it in:

```
DB_HOST=<rds-endpoint>.us-east-1.rds.amazonaws.com
DB_PORT=3306
DB_NAME=sentencebuilder
DB_USER=<username>
DB_PASSWORD="<password>"
```

- `.env` must sit in the **project root**, next to `pom.xml`.
- **Wrap the password in double quotes.** Without quotes, dotenv treats everything
  after a `#` as a comment and silently truncates the password, which shows up as
  `Access denied for user ...`.
- `DB_HOST` is the RDS **endpoint** (AWS Console → RDS → Databases → our instance →
  Connectivity & security), not the instance name.
- `DB_NAME` is the database *inside* the server (`sentencebuilder`), not the RDS
  instance identifier.
- `.env` is listed in `.gitignore`. Never commit it.

## 2. Allow your IP in the RDS security group

RDS rejects traffic from IPs it doesn't know. In the AWS Console:

1. RDS → Databases → our instance → **Connectivity & security**
2. Click the **VPC security group**
3. **Inbound rules → Edit inbound rules → Add rule**
   - Type: `MySQL/Aurora` (port 3306)
   - Source: `My IP`
4. Save

Do this again whenever your IP changes (e.g. switching between campus and home Wi-Fi).

## 3. Run the connection test

`DB.main` connects and prints the result.

### Option A: IntelliJ (easiest)

1. Open the Maven panel and click **Reload**.
2. Open `DB.java` and click the green ▶ next to `main`.

### Option B: Terminal

From the project root, generate the dependency classpath once (rerun it after
`pom.xml` changes):

```bash
./mvnw -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
```

Then run:

```bash
java -cp "$(cat target/classpath.txt)" src/main/java/com/team23/sentencebuilder/database/DB.java
```

> Plain `javac DB.java` will **not** work. `javac` doesn't read `pom.xml`, so it
> can't find dotenv or the MySQL driver (`package io.github.cdimascio.dotenv does not exist`).

> If `./mvnw` fails with `release version 21 not supported`, your terminal's Java
> is too old. Install JDK 21 (`brew install openjdk@21`) or point Maven at
> IntelliJ's JDK:
> `JAVA_HOME="/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home" ./mvnw ...`

### Expected output

```
Connected: jdbc:mysql://<rds-endpoint>:3306/sentencebuilder
```

## Using the connection in code

```java
try (Connection conn = DB.getConnection()) {
    // run queries
}
```

Always use try-with-resources so the connection is closed.

## Troubleshooting

| Error | Cause | Fix |
|---|---|---|
| `package io.github.cdimascio.dotenv does not exist` | Compiled with plain `javac` | Use IntelliJ or the `java -cp` command above |
| `Communications link failure` | Your IP isn't allowed through the security group (or the network blocks port 3306) | Add your IP (step 2); try a different network |
| `Access denied for user '...'` | Wrong username/password, or password truncated at `#` | Check credentials; wrap `DB_PASSWORD` in double quotes |
| `Unknown database '...'` | `DB_NAME` doesn't exist on the server | Use `sentencebuilder`; if missing, run `CREATE DATABASE sentencebuilder;` |
| `Could not find .env file` / values are `null` | Not running from the project root | `cd` to the folder containing `pom.xml` and `.env` |
