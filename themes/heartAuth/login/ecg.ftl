<#import "template.ftl" as layout>
<@layout.registrationLayout ; section>
    <#if section = "title">Authenticate with ECG
    <#elseif section = "header">Authenticate with ECG
    <#elseif section = "form">
        <p>Use your phone to authenticate with ECG.</p>

        <form action="${url.loginAction}" method="post">
            <button type="submit" name="confirm" value="1" class="pf-c-button pf-m-primary">
                Continue (mock success)
            </button>
            <button type="submit" name="cancel" value="1" class="pf-c-button pf-m-link">
                Cancel
            </button>
        </form>
    </#if>
</@layout.registrationLayout>
