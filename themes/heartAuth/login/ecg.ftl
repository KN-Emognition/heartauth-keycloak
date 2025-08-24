<#import "template.ftl" as layout>
<@layout.registrationLayout ; section>
<#if section = "title">
  Authenticate with ECG
<#elseif section = "header">
  Authenticate with ECG
<#elseif section = "form">
  <p>Authenticate with ECG</p>

  <p class="id">Challenge id (debug): ${challengeId}</p>

  <div id="status" aria-live="polite">Waiting for approval…</div>

  <!-- One-shot finalize post when SSE says terminal -->
  <form id="finalizeForm" method="post" action="${url.loginAction}" style="display:none">
    <input type="hidden" name="finalize" value="1"/>
  </form>

  <form method="post" action="${url.loginAction}" style="margin-top:1rem">
    <button name="cancel" value="1" class="pf-c-button pf-m-primary" type="submit">Cancel</button>
  </form>

  <script>
  (function () {
    var statusBox = document.getElementById('status');
    var finalizeForm = document.getElementById('finalizeForm');

    // All values are injected by the authenticator
    var challengeId = '${challengeId?js_string}';
    var rootId      = '${rootAuthSessionId?js_string}';
    var tabId       = '${tabId?js_string}';
    var pollMs      = ${pollMs?c};
    var watchBase   = '${watchBase?js_string}'; // e.g. https://host/auth/realms/<realm>/ecg

    var watchUrl = watchBase
                 + '/watch?root=' + encodeURIComponent(rootId)
                 + '&tab=' + encodeURIComponent(tabId)
                 + '&challengeId=' + encodeURIComponent(challengeId)
                 + '&pollMs=' + encodeURIComponent(pollMs);

    function setStatus(msg){ if (statusBox) statusBox.textContent = msg; }
    function finalizeOnce(){ if (finalizeForm.requestSubmit) finalizeForm.requestSubmit(); else finalizeForm.submit(); }

    function handle(payload) {
      var st = (payload && payload.state) || 'PENDING';
      if (st === 'APPROVED') { setStatus('Approved. Continuing…'); return finalizeOnce(); }
      if (st === 'DENIED')   { setStatus('Denied'); return finalizeOnce(); }
      if (st === 'EXPIRED' || st === 'NOT_FOUND') { setStatus('Challenge expired.'); return finalizeOnce(); }
      setStatus('Waiting for approval…');
    }

    function startSSE() {
      try {
        var es = new EventSource(watchUrl);
        es.onmessage = function (e) { try { handle(JSON.parse(e.data)); } catch(_) {} };
        es.onerror   = function () { try { es.close(); } catch(_) {} setTimeout(startSSE, 1500); };
      } catch (_) {
        setTimeout(startSSE, 1500);
      }
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', startSSE);
    else startSSE();
  })();
  </script>
</#if>
</@layout.registrationLayout>
