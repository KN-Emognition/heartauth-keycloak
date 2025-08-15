<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true displayWide=true>
  <#assign title = "Enroll your phone">
  <#nested "form">
    <div class="kc-form-wrapper">
      <h2>Scan to enroll your phone</h2>
      <img alt="enroll QR" src="${qrImage}" style="max-width:256px; width:100%; height:auto;"/>
      <p>Your app will generate a keypair and POST JSON to:</p>
      <code>${postUrl}</code>
      <pre>{"code":"${code}","device":"<id>","pubkey":"<base64url-ed25519>"}</pre>

      <form id="kc-form-login" action="${url.loginAction}" method="post" style="display:none">
        <input type="hidden" name="confirm" value="1"/>
      </form>

      <script>
        async function poll() {
          try {
            const r = await fetch(`${url.resourcesPath}/../../device-enroll/status?code=${'${code}'}&_=${Date.now()}`, { cache: 'no-cache' });
            const j = await r.json();
            if (j.status === 'confirmed') {
              document.getElementById('kc-form-login').submit();
              return;
            }
          } catch (e) {}
          setTimeout(poll, 1500);
        }
        poll();
      </script>
    </div>
  </#nested>
</@layout.registrationLayout>