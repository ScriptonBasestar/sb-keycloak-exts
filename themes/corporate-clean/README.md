# Corporate Clean Theme

A minimal and modern login theme for Keycloak, designed for enterprise environments.

## Features

- 🎨 Clean, modern design with gradient background
- 📱 Fully responsive (desktop, tablet, mobile)
- 🌍 Multi-language support (English, Korean, Japanese)
- ♿ WCAG 2.1 AA accessibility compliant
- 🎭 Social login provider styling (Kakao, Naver, Google, GitHub, LINE)
- ⚡ Smooth animations and transitions
- 🔒 Client-side form validation
- 🎯 Focus management for better UX

## Installation

### Using JAR (Recommended)

```bash
# Build theme
./gradlew :themes:corporateCleanJar

# Deploy to Keycloak
cp themes/build/libs/keycloak-theme-corporate-clean.jar $KEYCLOAK_HOME/providers/

# Rebuild Keycloak
$KEYCLOAK_HOME/bin/kc.sh build
```

### Direct Copy

```bash
cp -r themes/corporate-clean $KEYCLOAK_HOME/themes/
cp -r themes/corporate-base $KEYCLOAK_HOME/themes/

# Restart Keycloak
$KEYCLOAK_HOME/bin/kc.sh start
```

## Configuration

1. Login to Keycloak Admin Console
2. Select your realm
3. Go to **Realm Settings** → **Themes**
4. Select `corporate-clean` from **Login Theme** dropdown
5. Click **Save**

## Customization

### Colors

Edit `login/resources/css/login.css` or override CSS variables:

```css
:root {
    --corporate-primary: #2563eb;
    --corporate-primary-hover: #1d4ed8;
}
```

### Background

Change gradient or add image in `login/resources/css/login.css`:

```css
.corporate-clean-body {
    /* Gradient background */
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);

    /* Or image background */
    background: url('../img/background.jpg') center/cover;
}
```

### Logo

1. Add logo file: `login/resources/img/logo.svg`
2. Update `login/template.ftl`:

```html
<div class="corporate-clean-brand">
    <img src="${url.resourcesPath}/img/logo.svg" alt="Logo" style="max-height: 60px; margin-bottom: 1rem;" />
    <h1>${msg("loginTitleHtml",(realm.displayNameHtml!''))}</h1>
</div>
```

### Translation

Add or modify messages in:
- `login/messages/messages_en.properties`
- `login/messages/messages_ko.properties`
- `login/messages/messages_ja.properties`

## File Structure

```
corporate-clean/
├── theme.properties              # Theme metadata
├── README.md                     # This file
└── login/
    ├── theme.properties          # Login theme config
    ├── template.ftl              # Base layout
    ├── login.ftl                 # Login page
    ├── resources/
    │   ├── css/
    │   │   └── login.css         # Theme styles
    │   ├── js/
    │   │   └── login.js          # Client validation
    │   └── img/
    │       └── (assets)
    └── messages/
        ├── messages_en.properties
        ├── messages_ko.properties
        └── messages_ja.properties
```

## Social Login Providers

Pre-styled buttons for:

| Provider | Color | Border |
|----------|-------|--------|
| Kakao    | #FEE500 (Yellow) | #FEE500 |
| Naver    | #03C75A (Green) | #03C75A |
| Google   | White | #4285F4 (Blue) |
| GitHub   | #24292e (Dark) | #24292e |
| LINE     | Auto-styled | Auto-styled |

Add social providers in Keycloak Admin Console:
1. **Identity Providers** → **Add provider**
2. Select provider (e.g., `kakao`, `naver`)
3. Configure OAuth2 credentials

## Browser Support

- Chrome/Edge 90+
- Firefox 88+
- Safari 14+
- iOS Safari 14+
- Chrome Mobile

## Accessibility Features

- Semantic HTML structure
- ARIA labels and roles
- Keyboard navigation support
- Focus indicators
- Screen reader friendly
- High contrast mode support

## Screenshots

### Desktop Login
![Desktop](screenshots/desktop.png)

### Mobile Login
![Mobile](screenshots/mobile.png)

### Social Login
![Social](screenshots/social.png)

## Troubleshooting

### Theme not visible
- Ensure JAR is in `providers/` directory
- Run `kc.sh build` to rebuild Keycloak
- Restart Keycloak server

### Styles not loading
- Check browser console for errors
- Verify `theme.properties` includes `styles=css/login.css`
- Clear browser cache

### Translations missing
- Check property file encoding (UTF-8)
- Ensure locale is enabled in `theme.properties`
- Verify property keys match template references

## License

Part of the Keycloak Extensions project.

## Support

Report issues at: [GitHub Issues](https://github.com/yourusername/sb-keycloak-exts/issues)
