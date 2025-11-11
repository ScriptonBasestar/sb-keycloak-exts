<#import "template.ftl" as layout>
<@layout.emailLayout>
<!DOCTYPE html>
<html lang="${locale}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${msg("deletionConfirmationSubject")}</title>
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
            color: #dc2626;
            margin-bottom: 10px;
        }
        .alert-icon {
            font-size: 64px;
            margin: 20px 0;
        }
        h1 {
            color: #991b1b;
            font-size: 24px;
            margin-bottom: 20px;
        }
        .content {
            margin-bottom: 30px;
            color: #4b5563;
        }
        .warning-box {
            background-color: #fef2f2;
            border: 2px solid #fca5a5;
            border-radius: 8px;
            padding: 20px;
            margin: 20px 0;
        }
        .warning-box strong {
            color: #b91c1c;
            font-size: 18px;
        }
        .deletion-info {
            background-color: #fffbeb;
            border-left: 4px solid #f59e0b;
            border-radius: 4px;
            padding: 15px;
            margin: 20px 0;
        }
        .data-list {
            background-color: #f9fafb;
            border-radius: 6px;
            padding: 20px;
            margin: 20px 0;
        }
        .data-list ul {
            margin: 10px 0;
            padding-left: 20px;
        }
        .data-list li {
            margin: 8px 0;
            color: #6b7280;
        }
        .button-container {
            text-align: center;
            margin: 30px 0;
        }
        .button {
            display: inline-block;
            padding: 14px 32px;
            color: #ffffff !important;
            text-decoration: none;
            border-radius: 6px;
            font-weight: 600;
            font-size: 16px;
            margin: 5px;
        }
        .button-cancel {
            background-color: #10b981;
        }
        .button-cancel:hover {
            background-color: #059669;
        }
        .button-delete {
            background-color: #dc2626;
        }
        .button-delete:hover {
            background-color: #b91c1c;
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
            <div class="logo">⚠️ ${realmDisplayName!realmName}</div>
            <div class="alert-icon">🗑️</div>
        </div>

        <h1>${msg("deletionConfirmationTitle")}</h1>

        <div class="content">
            <p>${msg("deletionConfirmationGreeting", user.firstName!"")}</p>

            <div class="warning-box">
                <strong>⚠️ ${msg("deletionConfirmationWarningTitle")}</strong>
                <p>${msg("deletionConfirmationWarningBody")}</p>
            </div>

            <div class="deletion-info">
                <strong>📅 ${msg("deletionConfirmationGracePeriodTitle")}</strong><br>
                ${msg("deletionConfirmationGracePeriodBody", deletionDate!"")}
            </div>

            <div class="data-list">
                <strong>${msg("deletionConfirmationDataTitle")}</strong>
                <ul>
                    <li>${msg("deletionDataProfile")}</li>
                    <li>${msg("deletionDataPreferences")}</li>
                    <li>${msg("deletionDataConsents")}</li>
                    <li>${msg("deletionDataSessions")}</li>
                    <li>${msg("deletionDataHistory")}</li>
                </ul>
                <p><em>${msg("deletionDataAuditNote")}</em></p>
            </div>

            <p><strong>${msg("deletionConfirmationCancelTitle")}</strong></p>
            <p>${msg("deletionConfirmationCancelBody")}</p>

            <div class="button-container">
                <a href="${cancelLink}" class="button button-cancel">${msg("deletionButtonCancel")}</a>
                <a href="${link}" class="button button-delete">${msg("deletionButtonConfirm")}</a>
            </div>

            <p>${msg("deletionConfirmationExportReminder")}</p>
        </div>

        <div class="footer">
            <p>${msg("deletionConfirmationFooter")}</p>
            <p>&copy; ${.now?string("yyyy")} ${realmDisplayName!realmName}. ${msg("emailVerificationRights")}</p>
        </div>
    </div>
</body>
</html>
</@layout.emailLayout>
