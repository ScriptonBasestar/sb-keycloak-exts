<#import "template.ftl" as layout>
<@layout.emailLayout>
<!DOCTYPE html>
<html lang="${locale}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${msg("welcomeEmailSubject")}</title>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
            line-height: 1.6;
            color: #333;
            max-width: 600px;
            margin: 0 auto;
            padding: 20px;
            background-color: #f4f4f4;
        }
        .container {
            background-color: #ffffff;
            border-radius: 8px;
            padding: 40px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        .header {
            text-align: center;
            margin-bottom: 30px;
        }
        .logo {
            font-size: 32px;
            font-weight: bold;
            color: #10b981;
            margin-bottom: 10px;
        }
        .success-icon {
            font-size: 64px;
            margin: 20px 0;
        }
        h1 {
            color: #1f2937;
            font-size: 28px;
            margin-bottom: 20px;
            text-align: center;
        }
        .content {
            margin-bottom: 30px;
            color: #4b5563;
        }
        .feature-list {
            background-color: #f9fafb;
            border-radius: 6px;
            padding: 20px;
            margin: 20px 0;
        }
        .feature-item {
            display: flex;
            align-items: start;
            margin-bottom: 15px;
        }
        .feature-item:last-child {
            margin-bottom: 0;
        }
        .feature-icon {
            font-size: 20px;
            margin-right: 12px;
            margin-top: 2px;
        }
        .button-container {
            text-align: center;
            margin: 30px 0;
        }
        .button {
            display: inline-block;
            padding: 14px 32px;
            background-color: #10b981;
            color: #ffffff !important;
            text-decoration: none;
            border-radius: 6px;
            font-weight: 600;
            font-size: 16px;
            margin: 5px;
        }
        .button:hover {
            background-color: #059669;
        }
        .button-secondary {
            background-color: #6b7280;
        }
        .button-secondary:hover {
            background-color: #4b5563;
        }
        .info-box {
            background-color: #eff6ff;
            border-left: 4px solid: #3b82f6;
            border-radius: 4px;
            padding: 15px;
            margin: 20px 0;
        }
        .footer {
            margin-top: 40px;
            text-align: center;
            color: #9ca3af;
            font-size: 12px;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <div class="logo">🎉 ${realmDisplayName!realmName}</div>
            <div class="success-icon">✅</div>
        </div>

        <h1>${msg("welcomeEmailTitle")}</h1>

        <div class="content">
            <p>${msg("welcomeEmailGreeting", user.firstName!"", user.username)}</p>

            <p>${msg("welcomeEmailBody")}</p>

            <div class="feature-list">
                <div class="feature-item">
                    <div class="feature-icon">👤</div>
                    <div>
                        <strong>${msg("welcomeFeatureProfileTitle")}</strong><br>
                        ${msg("welcomeFeatureProfileDesc")}
                    </div>
                </div>
                <div class="feature-item">
                    <div class="feature-icon">🔒</div>
                    <div>
                        <strong>${msg("welcomeFeatureSecurityTitle")}</strong><br>
                        ${msg("welcomeFeatureSecurityDesc")}
                    </div>
                </div>
                <div class="feature-item">
                    <div class="feature-icon">⚙️</div>
                    <div>
                        <strong>${msg("welcomeFeatureSettingsTitle")}</strong><br>
                        ${msg("welcomeFeatureSettingsDesc")}
                    </div>
                </div>
            </div>

            <div class="button-container">
                <a href="${link}" class="button">${msg("welcomeButtonLogin")}</a>
                <a href="${profileLink!link}" class="button button-secondary">${msg("welcomeButtonProfile")}</a>
            </div>

            <div class="info-box">
                <strong>💡 ${msg("welcomeTipTitle")}</strong><br>
                ${msg("welcomeTipBody")}
            </div>
        </div>

        <div class="footer">
            <p>${msg("welcomeFooter")}</p>
            <p>${msg("welcomeSupport")}</p>
            <p>&copy; ${.now?string("yyyy")} ${realmDisplayName!realmName}. ${msg("emailVerificationRights")}</p>
        </div>
    </div>
</body>
</html>
</@layout.emailLayout>
