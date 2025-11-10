<#import "template.ftl" as layout>
<@layout.registeredLayout displayMessage=false; section>
    <#if section = "form">
        <div id="kc-info-message">
            <p class="instruction">${message.summary?no_esc}</p>
            <#if pageRedirectUri?has_content>
                <p><a href="${pageRedirectUri}" class="btn-corporate btn-corporate-primary">${kcSanitize(msg("backToApplication"))?no_esc}</a></p>
            <#elseif actionUri?has_content>
                <p><a href="${actionUri}" class="btn-corporate btn-corporate-primary">${kcSanitize(msg("proceedWithAction"))?no_esc}</a></p>
            <#elseif client?? && client.baseUrl?has_content>
                <p><a href="${client.baseUrl}" class="btn-corporate btn-corporate-primary">${kcSanitize(msg("backToApplication"))?no_esc}</a></p>
            </#if>
        </div>
    </#if>
</@layout.registeredLayout>
