<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=false; section>
    <#if section = "title">Scan to continue
    <#elseif section = "header">Scan to continue
    <#elseif section = "form">
        <p>Use the mobile app to approve the sign-in.</p>
        <div style="display:flex;justify-content:center;margin:1rem 0;">
            <img alt="QR" src="${qr}" width="280" height="280" style="border-radius:8px"/>
        </div>

        <form action="${url.loginAction}" method="post">
            <input type="hidden" name="session" value="${sessionId}">
            <div class="pf-c-form__actions">
                <button class="pf-c-button pf-m-primary" name="confirm" value="1" type="submit">I scanned it</button>
                <button class="pf-c-button pf-m-link" name="cancel" value="1" type="submit">Cancel</button>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
