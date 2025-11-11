<#import "template.ftl" as layout>
<@layout.emailLayout>
<!DOCTYPE html>
<html lang="${locale}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${msg("passwordChangedSubject")}</title>
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
            color: #f59e0b;
            margin-bottom: 10px;
        }
        .warning-icon {
            font-size: 64px;
            margin: 20px 0;
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
        .info-table {
            background-color: #f9fafb;
            border-radius: 6px;
            padding: 20px;
            margin: 20px 0;
        }
        .info-row {
            display: flex;
            padding: 10px 0;
            border-bottom: 1px solid #e5e7eb;
        }
        .info-row:last-child {
            border-bottom: none;
        }
        .info-label {
            font-weight: 600;
            width: 140px;
            color: #374151;
        }
        .info-value {
            color: #6b7280;
        }
        .security-notice {
            background-color: #fef2f2;
            border-left: 4px solid #ef4444;
            border-radius: 4px;
            padding: 15px;
            margin: 20px 0;
        }
        .security-notice strong {
            color: #b91c1c;
        }
        .button-container {
            text-align: center;
            margin: 30px 0;
        }
        .button {
            display: inline-block;
            padding: 14px 32px;
            background-color: #ef4444;
            color: #ffffff !important;
            text-decoration: none;
            border-radius: 6px;
            font-weight: 600;
            font-size: 16px;
        }
        .button:hover {
            background-color: #dc2626;
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
            <div class="logo">🔐 ${realmDisplayName!realmName}</div>
            <div class="warning-icon">🔔</div>
        </div>

        <h1>${msg("passwordChangedTitle")}</h1>

        <div class="content">
            <p>${msg("passwordChangedGreeting", user.firstName!"")}</p>

            <p>${msg("passwordChangedBody")}</p>

            <div class="info-table">
                <div class="info-row">
                    <div class="info-label">${msg("passwordChangedUsername")}</div>
                    <div class="info-value">${user.username}</div>
                </div>
                <div class="info-row">
                    <div class="info-label">${msg("passwordChangedEmail")}</div>
                    <div class="info-value">${user.email!"-"}</div>
                </div>
                <div class="info-row">
                    <div class="info-label">${msg("passwordChangedTime")}</div>
                    <div class="info-value">${.now?string("yyyy-MM-dd HH:mm:ss z")}</div>
                </div>
                <#if ipAddress??>
                <div class="info-row">
                    <div class="info-label">${msg("passwordChangedIpAddress")}</div>
                    <div class="info-value">${ipAddress}</div>
                </div>
                </#if>
            </div>

            <div class="security-notice">
                <strong>⚠️ ${msg("passwordChangedSecurityTitle")}</strong><br>
                ${msg("passwordChangedSecurityBody")}

                <div class="button-container">
                    <a href="${link}" class="button">${msg("passwordChangedSecurityButton")}</a>
                </div>
            </div>

            <p>${msg("passwordChangedConfirmation")}</p>
        </div>

        <div class="footer">
            <p>${msg("passwordChangedFooter")}</p>
            <p>&copy; ${.now?string("yyyy")} ${realmDisplayName!realmName}. ${msg("emailVerificationRights")}</p>
        </div>
    </div>
</body>
</html>
</@layout.emailLayout>
