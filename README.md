# Spend Analyser (Java 11, Spring Boot 2.7)

A multi-user web app that shows how much you spend each month and each year.

## Features
- **Accounts**: sign up / log in (passwords stored with BCrypt); every user sees only their own data
- **Transactions**: add, edit, delete income and expenses; filter by month, type, category or text; export to CSV
- **Dashboard**: income, spending, balance, savings rate, comparison with last month,
  end-of-month spending forecast, category doughnut chart, 6-month trend chart, recent transactions
- **Budgets**: monthly limit per category with progress bars; warning at 80% and alert when exceeded
- **Recurring items**: rent, salary, subscriptions repeat weekly / monthly / yearly and are
  recorded automatically (on login and every night)
- **Yearly report**: month-by-month chart and table, category breakdown with share %, average monthly
  spend, highest spending month, five biggest expenses, CSV export

## Requirements
- Java 11 or newer
- Maven (IntelliJ includes it)
- Internet connection in the browser (charts load Chart.js from a CDN)

No database install needed: data is saved in a built-in H2 file database in the `data/` folder.

## Run
    mvn spring-boot:run
Then open http://localhost:8080 and click "Create an account".

In IntelliJ: open `pom.xml` as a project, set the SDK to 11, then run `SpendAnalyserApplication`.

## Folder layout
    src/main/java/com/spendanalyser/
      SpendAnalyserApplication.java
      config/SecurityConfig.java          login rules
      model/                              AppUser, Transaction, Budget, RecurringRule, enums, categories
      repo/                               database access (Spring Data JPA)
      service/Analytics.java              all totals, budgets, trends, forecast
      service/RecurringService.java       creates due recurring transactions
      web/                                controllers: auth, dashboard, transactions, budgets, recurring, reports
    src/main/resources/
      application.properties
      templates/                          Thymeleaf pages
      static/css/app.css

## Change the currency
The symbol is written as &#8377; in the templates. Search and replace it (for example with $).

## Use MySQL instead of H2
Replace the h2 dependency in pom.xml with mysql-connector-j and change the `spring.datasource.*`
lines in application.properties.
