<#macro registeredLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false>
<!DOCTYPE html>
<html class="${properties.kcHtmlClass!}">

<head>
    <meta charset="utf-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="robots" content="noindex, nofollow">
    <meta name="viewport" content="width=device-width, initial-scale=1">

    <#if properties.meta?has_content>
        <#list properties.meta?split(' ') as meta>
            <meta name="${meta?split('==')[0]}" content="${meta?split('==')[1]}"/>
        </#list>
    </#if>

    <title>${msg("loginTitle",(realm.displayName!''))}</title>

    <link rel="icon" href="${url.resourcesPath}/img/favicon.ico" />

    <#if properties.stylesCommon?has_content>
        <#list properties.stylesCommon?split(' ') as style>
            <link href="${url.resourcesCommonPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    <#if properties.scripts?has_content>
        <#list properties.scripts?split(' ') as script>
            <script src="${url.resourcesPath}/${script}" type="text/javascript"></script>
        </#list>
    </#if>
</head>

<body class="${properties.kcBodyClass!} corporate-clean-body">
    <div class="corporate-clean-container">
        <div class="corporate-clean-card">
            <header class="corporate-clean-header">
                <#if realm.internationalizationEnabled  && locale.supported?size gt 1>
                    <div class="corporate-clean-locale">
                        <div id="kc-locale">
                            <div id="kc-locale-wrapper">
                                <div id="kc-locale-dropdown" class="menu-button-links">
                                    <button type="button" id="kc-current-locale-link">${locale.current}</button>
                                    <ul>
                                        <#list locale.supported as l>
                                            <li class="kc-dropdown-item">
                                                <a href="${l.url}">${l.label}</a>
                                            </li>
                                        </#list>
                                    </ul>
                                </div>
                            </div>
                        </div>
                    </div>
                </#if>

                <#if !(auth?has_content && auth.showUsername() && !auth.showResetCredentials())>
                    <#if displayRequiredFields>
                        <div class="${properties.kcContentWrapperClass!}">
                            <div class="${properties.kcLabelWrapperClass!} subtitle">
                                <span class="subtitle"><span class="required">*</span> ${msg("requiredFields")}</span>
                            </div>
                        </div>
                    </#if>
                <#else>
                    <#if displayRequiredFields>
                        <div class="${properties.kcContentWrapperClass!}">
                            <div class="${properties.kcLabelWrapperClass!} subtitle">
                                <span class="subtitle"><span class="required">*</span> ${msg("requiredFields")}</span>
                            </div>
                            <div class="username">
                                <span id="kc-attempted-username">${auth.attemptedUsername}</span>
                                <a id="reset-login" href="${url.loginRestartFlowUrl}">
                                    <div class="kc-login-tooltip">
                                        <i class="${properties.kcResetFlowIcon!}"></i>
                                        <span class="kc-tooltip-text">${msg("restartLoginTooltip")}</span>
                                    </div>
                                </a>
                            </div>
                        </div>
                    <#else>
                        <div class="username">
                            <span id="kc-attempted-username">${auth.attemptedUsername}</span>
                            <a id="reset-login" href="${url.loginRestartFlowUrl}">
                                <div class="kc-login-tooltip">
                                    <i class="${properties.kcResetFlowIcon!}"></i>
                                    <span class="kc-tooltip-text">${msg("restartLoginTooltip")}</span>
                                </div>
                            </a>
                        </div>
                    </#if>
                </#if>
            </header>

            <div class="corporate-clean-brand">
                <h1>${msg("loginTitleHtml",(realm.displayNameHtml!''))}</h1>
            </div>

            <div class="corporate-clean-content">
                <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
                    <div class="alert alert-${message.type}">
                        <#if message.type = 'success'><span class="${properties.kcFeedbackSuccessIcon!}"></span></#if>
                        <#if message.type = 'warning'><span class="${properties.kcFeedbackWarningIcon!}"></span></#if>
                        <#if message.type = 'error'><span class="${properties.kcFeedbackErrorIcon!}"></span></#if>
                        <#if message.type = 'info'><span class="${properties.kcFeedbackInfoIcon!}"></span></#if>
                        <span class="kc-feedback-text">${kcSanitize(message.summary)?no_esc}</span>
                    </div>
                </#if>

                <#nested "form">

                <#if displayInfo>
                    <div class="corporate-clean-info">
                        <#nested "info">
                    </div>
                </#if>
            </div>

            <footer class="corporate-clean-footer">
                <p>&copy; ${.now?string('yyyy')} ${realm.displayName!''}. ${msg("allRightsReserved")}</p>
            </footer>
        </div>
    </div>
</body>
</html>
</#macro>
