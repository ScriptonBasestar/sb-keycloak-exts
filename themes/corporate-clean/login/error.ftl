<#import "template.ftl" as layout>
<@layout.registeredLayout displayMessage=false; section>
    <#if section = "form">
        <div id="kc-error-message">
            <p class="instruction">${message.summary?no_esc}</p>
            <#if client?? && client.baseUrl?has_content>
                <p><a id="backToApplication" href="${client.baseUrl}" class="btn-corporate btn-corporate-primary">${kcSanitize(msg("backToApplication"))?no_esc}</a></p>
            </#if>
        </div>
    </#if>
</@layout.registeredLayout>
