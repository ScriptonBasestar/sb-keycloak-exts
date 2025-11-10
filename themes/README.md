# Keycloak Corporate Themes

Modern, clean, and enterprise-ready login themes for Keycloak.

## Overview

This module provides professionally designed login themes optimized for enterprise environments. All themes are built with accessibility, internationalization, and modern web standards in mind.

## Available Themes

### Corporate Clean

A minimal, modern login theme with:
- Clean and professional design
- Gradient background
- Smooth animations
- Full responsive support
- Multi-language support (English, Korean, Japanese)
- Social login provider styling (Kakao, Naver, Google, GitHub, LINE)

**Preview:**

![Corporate Clean Theme](corporate-clean/preview.png)

## Installation

### Method 1: JAR Deployment (Recommended)

Build the theme JAR and deploy to Keycloak:

```bash
# Build theme JARs
./gradlew :themes:buildThemes

# Deploy to Keycloak (requires KEYCLOAK_HOME)
./gradlew :themes:deployThemesLocal

# Or manually copy
cp themes/build/libs/keycloak-theme-corporate-*.jar $KEYCLOAK_HOME/providers/

# Rebuild Keycloak
$KEYCLOAK_HOME/bin/kc.sh build
```

### Method 2: Directory Copy

Copy theme directories directly:

```bash
cp -r themes/corporate-clean $KEYCLOAK_HOME/themes/
cp -r themes/corporate-base $KEYCLOAK_HOME/themes/

# Restart Keycloak
$KEYCLOAK_HOME/bin/kc.sh start
```

## Usage

1. Login to Keycloak Admin Console
2. Navigate to **Realm Settings** → **Themes**
3. Select `corporate-clean` from the **Login Theme** dropdown
4. Click **Save**

## Customization

### Changing Colors

Edit `corporate-base/login/resources/css/base.css`:

```css
:root {
    --corporate-primary: #2563eb;        /* Primary color */
    --corporate-primary-hover: #1d4ed8;  /* Hover state */
    /* ... other variables */
}
```

### Changing Background

Edit `corporate-clean/login/resources/css/login.css`:

```css
.corporate-clean-body {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    /* Or use an image */
    /* background: url('../img/background.jpg') center/cover; */
}
```

### Adding Logo

1. Add your logo to `corporate-clean/login/resources/img/logo.svg`
2. Edit `corporate-clean/login/template.ftl`:

```html
<div class="corporate-clean-brand">
    <img src="${url.resourcesPath}/img/logo.svg" alt="Logo" />
    <h1>${msg("loginTitleHtml",(realm.displayNameHtml!''))}</h1>
</div>
```

### Multi-Language Support

Add or modify messages in:
- `corporate-clean/login/messages/messages_en.properties` (English)
- `corporate-clean/login/messages/messages_ko.properties` (Korean)
- `corporate-clean/login/messages/messages_ja.properties` (Japanese)

Example:

```properties
# messages_en.properties
loginTitle=Sign In to Your Account
doLogIn=Sign In

# messages_ko.properties
loginTitle=계정에 로그인
doLogIn=로그인
```

## Theme Structure

```
themes/
├── corporate-base/              # Base theme (parent)
│   ├── theme.properties
│   └── login/
│       ├── theme.properties
│       └── resources/
│           └── css/
│               └── base.css     # Common variables & styles
│
└── corporate-clean/             # Clean theme (child)
    ├── theme.properties         # parent=corporate-base
    ├── README.md
    └── login/
        ├── theme.properties
        ├── template.ftl         # Base layout
        ├── login.ftl            # Login page
        ├── resources/
        │   ├── css/
        │   │   └── login.css    # Theme-specific styles
        │   ├── js/
        │   │   └── login.js     # Client-side validation
        │   └── img/
        │       └── (assets)
        └── messages/
            ├── messages_en.properties
            ├── messages_ko.properties
            └── messages_ja.properties
```

## Supported Pages

Currently implemented:
- ✅ Login (`login.ftl`)
- ✅ Base template (`template.ftl`)

Coming soon:
- Registration
- Forgot password
- Email verification
- Error pages
- Account management

## Social Login Provider Styling

Pre-configured styles for:
- **Kakao** (카카오) - Yellow brand color
- **Naver** (네이버) - Green brand color
- **Google** - Blue brand color
- **GitHub** - Dark brand color
- **LINE** (라인) - Green brand color

Providers are automatically detected and styled when configured in Keycloak.

## Browser Support

- Chrome/Edge 90+
- Firefox 88+
- Safari 14+
- Mobile browsers (iOS Safari, Chrome Mobile)

## Accessibility

- WCAG 2.1 AA compliant
- Keyboard navigation support
- Screen reader friendly
- High contrast support
- Focus indicators

## Development

### Build

```bash
# Build all themes
./gradlew :themes:build

# Build specific theme
./gradlew :themes:corporateCleanJar
```

### Hot Reload (Development)

```bash
# Enable Keycloak dev mode
$KEYCLOAK_HOME/bin/kc.sh start-dev --spi-theme-static-max-age=-1 --spi-theme-cache-themes=false --spi-theme-cache-templates=false

# Make changes to theme files
# Refresh browser to see changes (no rebuild required)
```

### Testing

1. Start local Keycloak stack:
```bash
docker-compose up -d
```

2. Deploy theme:
```bash
make themes
```

3. Access Keycloak at http://localhost:8080
4. Configure realm to use `corporate-clean` theme
5. Test login flow

## Troubleshooting

### Theme not showing in dropdown

- Ensure theme JAR is in `$KEYCLOAK_HOME/providers/`
- Run `kc.sh build` to rebuild Keycloak
- Check `theme.properties` files exist
- Restart Keycloak

### Styles not loading

- Clear browser cache
- Check browser console for 404 errors
- Verify `theme.properties` includes `styles=css/login.css`
- Check file paths in template files

### Messages not translated

- Ensure `messages_XX.properties` files exist
- Check `theme.properties` includes correct locales
- Verify property keys match template references
- Restart Keycloak after adding new locales

## Contributing

To create a new theme:

1. Copy `corporate-clean` to `corporate-{name}`
2. Update `theme.properties` files
3. Customize CSS, templates, and messages
4. Add JAR task to `build.gradle`
5. Update this README

## License

Part of the Keycloak Extensions project.
See root [LICENSE](../LICENSE) for details.

## Support

- Issues: [GitHub Issues](https://github.com/yourusername/sb-keycloak-exts/issues)
- Docs: [Documentation](docs/)
- Examples: See `corporate-clean` theme

## Resources

- [Keycloak Themes Documentation](https://www.keycloak.org/docs/latest/server_development/#_themes)
- [FreeMarker Template Guide](https://freemarker.apache.org/docs/)
- [Keycloak Server Development](https://www.keycloak.org/docs/latest/server_development/)
