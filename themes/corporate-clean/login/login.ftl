<#import "template.ftl" as layout>
<@layout.registeredLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>
    <#if section = "form">
        <div id="kc-form">
            <div id="kc-form-wrapper">
                <#if realm.password>
                    <form id="kc-form-login" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
                        <div class="form-group">
                            <label for="username" class="form-label">
                                <#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if>
                            </label>

                            <input tabindex="1"
                                   id="username"
                                   class="input-corporate <#if messagesPerField.existsError('username','password')>input-error</#if>"
                                   name="username"
                                   value="${(login.username!'')}"
                                   type="text"
                                   autofocus
                                   autocomplete="off"
                                   aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>"
                            />

                            <#if messagesPerField.existsError('username','password')>
                                <span class="error-message" aria-live="polite">
                                    ${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}
                                </span>
                            </#if>
                        </div>

                        <div class="form-group">
                            <label for="password" class="form-label">${msg("password")}</label>

                            <input tabindex="2"
                                   id="password"
                                   class="input-corporate <#if messagesPerField.existsError('username','password')>input-error</#if>"
                                   name="password"
                                   type="password"
                                   autocomplete="off"
                                   aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>"
                            />
                        </div>

                        <div class="form-group form-actions">
                            <div class="form-checkbox">
                                <#if realm.rememberMe && !usernameEditDisabled??>
                                    <input tabindex="3" id="rememberMe" name="rememberMe" type="checkbox" <#if login.rememberMe??>checked</#if>>
                                    <label for="rememberMe">${msg("rememberMe")}</label>
                                </#if>
                            </div>

                            <div class="form-link">
                                <#if realm.resetPasswordAllowed>
                                    <a tabindex="5" href="${url.loginResetCredentialsUrl}">${msg("doForgotPassword")}</a>
                                </#if>
                            </div>
                        </div>

                        <div class="form-group">
                            <input type="hidden" id="id-hidden-input" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>/>
                            <button tabindex="4" class="btn-corporate btn-corporate-primary btn-block" name="login" id="kc-login" type="submit">${msg("doLogIn")}</button>
                        </div>
                    </form>
                </#if>
            </div>

            <#if realm.password && social.providers??>
                <div id="kc-social-providers" class="social-providers">
                    <hr class="divider"/>
                    <h4>${msg("identity-provider-login-label")}</h4>
                    <ul class="social-providers-list">
                        <#list social.providers as p>
                            <li>
                                <a id="social-${p.alias}"
                                   class="btn-social btn-social-${p.providerId}"
                                   href="${p.loginUrl}">
                                    <#if p.iconClasses?has_content>
                                        <i class="${p.iconClasses!}" aria-hidden="true"></i>
                                    </#if>
                                    <span>${p.displayName!}</span>
                                </a>
                            </li>
                        </#list>
                    </ul>
                </div>
            </#if>
        </div>
    <#elseif section = "info" >
        <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
            <div id="kc-registration-container">
                <div id="kc-registration">
                    <span>${msg("noAccount")} <a tabindex="6" href="${url.registrationUrl}">${msg("doRegister")}</a></span>
                </div>
            </div>
        </#if>
    </#if>

</@layout.registeredLayout>
