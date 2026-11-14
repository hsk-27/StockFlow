# StockFlow &bull; GitHub Web & Browser Upload Guide

This guide provides instructions for publishing the **StockFlow** repository to GitHub using your web browser or command line.

---

## ⚡ Important: Browser File Size Limits
> [!IMPORTANT]
> GitHub Web has a strict **25 MB per-file upload limit**. Compiled binaries like `target/stockflow-backend-1.0.0-SNAPSHOT.jar` (~65 MB) will be rejected by the web interface. 
> 
> The project has been cleaned with `mvn clean` so that all source files, configurations, scripts, and documentation are lightweight and ready for upload. Build artifacts are generated on-demand by running `./mvnw.cmd test` or via GitHub Actions CI.

---

## Method 1: Upload via GitHub Web in Your Browser

### Step 1: Create a New Repository on GitHub
1. Open your web browser and navigate to [https://github.com/new](https://github.com/new).
2. Set the **Repository name** to: `StockFlow` (or `stockflow-commerce-engine`).
3. Set visibility to **Public** (recommended for portfolio showcase) or **Private**.
4. **Do NOT** check *"Add a README file"*, *"Add .gitignore"*, or *"Choose a license"* (the repository already contains comprehensive versions of these files).
5. Click **Create repository**.

### Step 2: Upload Files via Web Drag-and-Drop
1. On the new repository's setup page, click the link that says **"uploading an existing file"** (or click the **"Add file"** dropdown $\to$ **"Upload files"**).
2. Open Windows File Explorer and navigate to:
   ```
   C:\Users\Dell\.gemini\antigravity\scratch\StockFlow
   ```
3. Select the following files and folders:
   - `src/` (Entire folder containing all 15 Java domain packages, static UI, and Flyway SQL migrations)
   - `.github/` (CI/CD automated workflow pipelines)
   - `.mvn/` (Maven wrapper binaries and configuration)
   - `docs/` (Architecture, API specs, testing, and deployment documentation)
   - `scripts/` (Automated helper scripts)
   - `pom.xml` (Maven project definition and pinned dependencies)
   - `mvnw` and `mvnw.cmd` (Portable Maven wrappers)
   - `README.md` (Full portfolio documentation with mermaid diagrams)
   - `Dockerfile` & `docker-compose.yml` (Production container configurations)
   - `requests.http` (Complete 11-module executable API testing script)
   - `LICENSE`, `SECURITY.md`, `CONTRIBUTING.md`, `.gitignore`, `.dockerignore`, `.env.example`
   *(Do **not** upload `.git/` or `target/` folders)*.
4. Drag and drop the selected files into the GitHub browser upload box.
5. In the **Commit changes** box at the bottom:
   - Commit title: `feat: initial release of StockFlow Multi-Vendor Commerce Platform`
   - Description: `Production-grade Java 21 & Spring Boot 3.3.5 commerce engine with 73/73 tests, Shopify Polaris UI, and zero-overselling guarantees.`
6. Ensure **"Commit directly to the main branch"** is selected, and click **Commit changes**.

---

## Method 2: Instant 1-Click Git Push (Recommended)

Because the project already has an initialized Git repository with all commit history and 73 test guarantees intact, pushing via Git CLI takes 5 seconds:

```bash
# 1. Open PowerShell and navigate to the project
cd C:\Users\Dell\.gemini\antigravity\scratch\StockFlow

# 2. Add your new GitHub repository as the remote origin
git remote add origin https://github.com/<YOUR_GITHUB_USERNAME>/StockFlow.git

# 3. Rename branch to main if needed and push
git branch -M main
git push -u origin main
```

---

## 🎯 What GitHub Evaluators & Visitors Will See
Once uploaded, your repository will display:
1. **GitHub Actions CI Badge (`StockFlow CI`)**: Automatically verifies Java 21 compilation and PostgreSQL 16 test suites on every push.
2. **Shopify Polaris Design System**: Interactive web showcase documented with screenshots and step-by-step guides.
3. **OpenAPI 3.0 & Swagger UI Integration**: Ready-to-explore REST endpoints.
4. **Automated Test Badges**: 73/73 passing tests across 22 test classes.
