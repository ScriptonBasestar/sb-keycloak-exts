# Theme Installation Guide

Complete guide for installing Keycloak corporate themes.

## Prerequisites

- Keycloak 26.0.7 or later
- Java 21 (if building from source)
- `$KEYCLOAK_HOME` environment variable set (for automated deployment)

## Installation Methods

### Method 1: JAR Deployment (Production Recommended)

This method packages the theme as a JAR file for clean deployment.

**Step 1: Build Theme JAR**

```bash
# Build all themes
./gradlew :themes:buildThemes

# Or build specific theme
./gradlew :themes:corporateCleanJar

# Output: themes/build/libs/keycloak-theme-corporate-*.jar
```

**Step 2: Deploy to Keycloak**

Option A: Automated deployment (requires `KEYCLOAK_HOME`)

```bash
./gradlew :themes:deployThemesLocal
```

Option B: Manual deployment

```bash
# Copy JAR to providers directory
cp themes/build/libs/keycloak-theme-corporate-clean.jar $KEYCLOAK_HOME/providers/

# Or all themes
cp themes/build/libs/keycloak-theme-*.jar $KEYCLOAK_HOME/providers/
```

**Step 3: Rebuild Keycloak**

```bash
cd $KEYCLOAK_HOME
./bin/kc.sh build
```

**Step 4: Restart Keycloak**

```bash
# Production mode
./bin/kc.sh start

# Or development mode
./bin/kc.sh start-dev
```

### Method 2: Directory Copy (Development)

This method directly copies theme files for quick iteration.

**Step 1: Copy Theme Directories**

```bash
# Copy themes
cp -r themes/corporate-base $KEYCLOAK_HOME/themes/
cp -r themes/corporate-clean $KEYCLOAK_HOME/themes/

# Verify
ls $KEYCLOAK_HOME/themes/
# Should show: corporate-base, corporate-clean
```

**Step 2: Restart Keycloak**

```bash
cd $KEYCLOAK_HOME
./bin/kc.sh start
```

No rebuild required for directory-based themes.

### Method 3: Docker Deployment

**Option A: Using docker-compose**

```yaml
# docker-compose.yml
services:
  keycloak:
    image: quay.io/keycloak/keycloak:26.0.7
    volumes:
      - ./themes/build/libs/keycloak-theme-corporate-clean.jar:/opt/keycloak/providers/keycloak-theme-corporate-clean.jar
    environment:
      - KC_BOOTSTRAP_ADMIN_USERNAME=admin
      - KC_BOOTSTRAP_ADMIN_PASSWORD=admin
    command: start-dev
```

```bash
# Build theme first
./gradlew :themes:buildThemes

# Start Keycloak
docker-compose up -d
```

**Option B: Custom Dockerfile**

```dockerfile
FROM quay.io/keycloak/keycloak:26.0.7

# Copy theme JAR
COPY themes/build/libs/keycloak-theme-corporate-clean.jar /opt/keycloak/providers/

# Build Keycloak with theme
RUN /opt/keycloak/bin/kc.sh build

# Production entrypoint
ENTRYPOINT ["/opt/keycloak/bin/kc.sh", "start"]
```

```bash
# Build image
docker build -t keycloak-with-themes .

# Run
docker run -p 8080:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  keycloak-with-themes
```

### Method 4: Kubernetes Deployment

**Using ConfigMap**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: keycloak-themes
data:
  keycloak-theme-corporate-clean.jar: |
    # Base64 encoded JAR content
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: keycloak
spec:
  template:
    spec:
      containers:
      - name: keycloak
        image: quay.io/keycloak/keycloak:26.0.7
        volumeMounts:
        - name: themes
          mountPath: /opt/keycloak/providers/keycloak-theme-corporate-clean.jar
          subPath: keycloak-theme-corporate-clean.jar
      volumes:
      - name: themes
        configMap:
          name: keycloak-themes
```

**Using InitContainer**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: keycloak
spec:
  template:
    spec:
      initContainers:
      - name: theme-downloader
        image: busybox
        command:
        - sh
        - -c
        - |
          wget -O /providers/keycloak-theme-corporate-clean.jar \
            https://your-artifact-repo/keycloak-theme-corporate-clean.jar
        volumeMounts:
        - name: providers
          mountPath: /providers
      containers:
      - name: keycloak
        image: quay.io/keycloak/keycloak:26.0.7
        volumeMounts:
        - name: providers
          mountPath: /opt/keycloak/providers
      volumes:
      - name: providers
        emptyDir: {}
```

## Activating the Theme

### Via Admin Console (Recommended)

1. Navigate to http://localhost:8080 (or your Keycloak URL)
2. Login to Admin Console
3. Select your realm (or create new one)
4. Go to **Realm Settings** → **Themes** tab
5. In **Login Theme** dropdown, select `corporate-clean`
6. Click **Save**
7. Test by logging out and accessing login page

### Via Realm Import

Create `realm-config.json`:

```json
{
  "realm": "your-realm",
  "loginTheme": "corporate-clean",
  "accountTheme": "keycloak.v2",
  "adminTheme": "keycloak.v2",
  "emailTheme": "keycloak"
}
```

Import:

```bash
$KEYCLOAK_HOME/bin/kc.sh import --file realm-config.json
```

### Via CLI (kcadm)

```bash
# Login
$KEYCLOAK_HOME/bin/kcadm.sh config credentials \
  --server http://localhost:8080 \
  --realm master \
  --user admin \
  --password admin

# Set theme
$KEYCLOAK_HOME/bin/kcadm.sh update realms/your-realm \
  -s loginTheme=corporate-clean
```

## Verification

### Check Theme is Loaded

**View available themes:**

```bash
# Directory-based themes
ls $KEYCLOAK_HOME/themes/

# JAR-based themes
unzip -l $KEYCLOAK_HOME/providers/keycloak-theme-corporate-clean.jar | grep theme.properties
```

**Check Keycloak logs:**

```bash
tail -f $KEYCLOAK_HOME/data/log/keycloak.log
# Look for: "Theme 'corporate-clean' loaded"
```

### Test Login Page

1. Open browser to: `http://localhost:8080/realms/your-realm/account`
2. You'll be redirected to login page
3. Verify:
   - Corporate Clean styling is applied
   - Gradient background visible
   - Social login buttons styled correctly
   - Language switcher works

### Test Multi-Language

1. On login page, click language dropdown (top-right)
2. Select **한국어** (Korean)
3. Verify all text is translated
4. Repeat for **日本語** (Japanese)

## Troubleshooting

### Theme Not Appearing in Dropdown

**Cause:** Theme not properly registered or Keycloak not rebuilt.

**Solution:**

```bash
# For JAR themes
cd $KEYCLOAK_HOME
./bin/kc.sh build
./bin/kc.sh start

# For directory themes
ls themes/corporate-clean/theme.properties
# Must exist and be readable
```

### Styles Not Loading

**Cause:** CSS files missing or incorrect path.

**Solution:**

```bash
# Check theme structure
ls themes/corporate-clean/login/resources/css/login.css

# Check theme.properties
cat themes/corporate-clean/login/theme.properties
# Must contain: styles=css/login.css
```

**Browser check:**
1. Open browser DevTools (F12)
2. Go to Network tab
3. Reload login page
4. Look for 404 errors on CSS files

### Translations Not Working

**Cause:** Message files missing or wrong encoding.

**Solution:**

```bash
# Check message files exist
ls themes/corporate-clean/login/messages/

# Check encoding (must be UTF-8)
file -i themes/corporate-clean/login/messages/messages_ko.properties
# Should show: charset=utf-8

# Check theme.properties
cat themes/corporate-clean/login/theme.properties
# Must contain: locales=en,ko,ja
```

### Theme Partially Broken

**Cause:** Missing parent theme or incorrect inheritance.

**Solution:**

```bash
# Ensure base theme exists
ls themes/corporate-base/

# Check parent reference
cat themes/corporate-clean/theme.properties
# Must contain: parent=corporate-base

# For JAR deployment, ensure base theme is included
unzip -l themes/build/libs/keycloak-theme-corporate-clean.jar | grep corporate-base
```

### Docker Container Issues

**Cause:** JAR not copied before build or volume mount issues.

**Solution:**

```bash
# Build theme first
./gradlew :themes:buildThemes

# Check JAR exists
ls -lh themes/build/libs/

# Verify volume mount
docker exec keycloak ls /opt/keycloak/providers/

# Rebuild Keycloak in container
docker exec keycloak /opt/keycloak/bin/kc.sh build
docker restart keycloak
```

## Development Mode (Hot Reload)

For theme development, enable hot reload:

```bash
$KEYCLOAK_HOME/bin/kc.sh start-dev \
  --spi-theme-static-max-age=-1 \
  --spi-theme-cache-themes=false \
  --spi-theme-cache-templates=false
```

Changes to `.ftl` and `.css` files take effect immediately (browser refresh only).

**Note:** Changes to `theme.properties` still require restart.

## Uninstallation

### Remove JAR Theme

```bash
rm $KEYCLOAK_HOME/providers/keycloak-theme-corporate-*.jar
cd $KEYCLOAK_HOME
./bin/kc.sh build
./bin/kc.sh start
```

### Remove Directory Theme

```bash
rm -rf $KEYCLOAK_HOME/themes/corporate-clean
rm -rf $KEYCLOAK_HOME/themes/corporate-base
# Restart Keycloak
```

### Reset Realm Theme

Via Admin Console:
1. Realm Settings → Themes
2. Set Login Theme to `keycloak` (default)
3. Save

Via CLI:

```bash
$KEYCLOAK_HOME/bin/kcadm.sh update realms/your-realm \
  -s loginTheme=keycloak
```

## Next Steps

- [Customization Guide](CUSTOMIZATION.md)
- [Theme Development](THEMING_GUIDE.md)
- [Troubleshooting](../TROUBLESHOOTING.md)
