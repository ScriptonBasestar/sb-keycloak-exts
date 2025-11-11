<#import "template.ftl" as layout>
<@layout.emailLayout>
<!DOCTYPE html>
<html lang="${locale}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${msg("emailVerificationSubject")}</title>
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
            color: #2563eb;
            margin-bottom: 10px;
        }
        h1 {
            color: #1f2937;
            font-size: 24px;
            margin-bottom: 20px;
        }
        .content {
            margin-bottom: 30px;
            color: #4b5563;
        }
        .button-container {
            text-align: center;
            margin: 30px 0;
        }
        .button {
            display: inline-block;
            padding: 14px 32px;
            background-color: #2563eb;
            color: #ffffff !important;
            text-decoration: none;
            border-radius: 6px;
            font-weight: 600;
            font-size: 16px;
        }
        .button:hover {
            background-color: #1d4ed8;
        }
        .alternative-link {
            margin-top: 20px;
            padding: 15px;
            background-color: #f3f4f6;
            border-radius: 6px;
            word-break: break-all;
            font-size: 12px;
            color: #6b7280;
        }
        .footer {
            margin-top: 40px;
            text-align: center;
            color: #9ca3af;
            font-size: 12px;
        }
        .expiry-notice {
            margin-top: 20px;
            padding: 12px;
            background-color: #fef3c7;
            border-left: 4px solid #f59e0b;
            border-radius: 4px;
            color: #92400e;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <div class="logo">🔐 ${realmDisplayName!realmName}</div>
        </div>

        <h1>${msg("emailVerificationTitle")}</h1>

        <div class="content">
            <p>${msg("emailVerificationGreeting", user.firstName!"")}</p>

            <p>${msg("emailVerificationBody")}</p>

            <div class="button-container">
                <a href="${link}" class="button">${msg("emailVerificationButton")}</a>
            </div>

            <div class="alternative-link">
                <strong>${msg("emailVerificationAlternativeText")}</strong><br>
                <a href="${link}">${link}</a>
            </div>

            <div class="expiry-notice">
                <strong>⏰ ${msg("emailVerificationExpiryTitle")}</strong><br>
                ${msg("emailVerificationExpiryBody")}
            </div>
        </div>

        <div class="footer">
            <p>${msg("emailVerificationFooter")}</p>
            <p>${msg("emailVerificationIgnore")}</p>
            <p>&copy; ${.now?string("yyyy")} ${realmDisplayName!realmName}. ${msg("emailVerificationRights")}</p>
        </div>
    </div>
</body>
</html>
</@layout.emailLayout>
