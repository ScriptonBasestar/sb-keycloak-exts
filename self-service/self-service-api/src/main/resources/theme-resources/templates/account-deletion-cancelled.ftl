<#import "template.ftl" as layout>
<@layout.emailLayout>
<!DOCTYPE html>
<html lang="${locale}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${msg("deletionCancelledSubject")}</title>
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
            color: #065f46;
            font-size: 24px;
            margin-bottom: 20px;
            text-align: center;
        }
        .content {
            margin-bottom: 30px;
            color: #4b5563;
        }
        .success-box {
            background-color: #d1fae5;
            border: 2px solid #6ee7b7;
            border-radius: 8px;
            padding: 20px;
            margin: 20px 0;
            text-align: center;
        }
        .success-box strong {
            color: #065f46;
            font-size: 18px;
        }
        .info-box {
            background-color: #eff6ff;
            border-left: 4px solid #3b82f6;
            border-radius: 4px;
            padding: 15px;
            margin: 20px 0;
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
        }
        .button:hover {
            background-color: #059669;
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
            <div class="logo">✅ ${realmDisplayName!realmName}</div>
            <div class="success-icon">🎉</div>
        </div>

        <h1>${msg("deletionCancelledTitle")}</h1>

        <div class="content">
            <p>${msg("deletionCancelledGreeting", user.firstName!"")}</p>

            <div class="success-box">
                <strong>✅ ${msg("deletionCancelledSuccessTitle")}</strong>
                <p>${msg("deletionCancelledSuccessBody")}</p>
            </div>

            <p>${msg("deletionCancelledBody")}</p>

            <div class="info-box">
                <strong>💡 ${msg("deletionCancelledTipTitle")}</strong><br>
                ${msg("deletionCancelledTipBody")}
            </div>

            <div class="button-container">
                <a href="${link}" class="button">${msg("deletionCancelledButtonLogin")}</a>
            </div>

            <p>${msg("deletionCancelledClosing")}</p>
        </div>

        <div class="footer">
            <p>${msg("deletionCancelledFooter")}</p>
            <p>&copy; ${.now?string("yyyy")} ${realmDisplayName!realmName}. ${msg("emailVerificationRights")}</p>
        </div>
    </div>
</body>
</html>
</@layout.emailLayout>
