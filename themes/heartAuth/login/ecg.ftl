<#import "template.ftl" as layout>
<@layout.registrationLayout ; section>
<#if section = "title">
  Authenticate with ECG
<#elseif section = "header">
  Authenticate with ECG
<#elseif section = "form">
  <p>
    Authenticate with ECG
  </p>
  <p class="id">
    Challenge id for debugging: ${challengeId}
  </p>
  <form id="pollForm" method="post">
    <input type="hidden" name="poll" value="1"/>
  </form>
  <form method="post" style="margin-top:1rem">
    <button name="cancel" value="1"  class="pf-c-button pf-m-primary" type="submit">
      Cancel
    </button>
  </form>
</#if>
<script>
  const pollMs = ${pollMs?c};
  function poll(){ document.getElementById('pollForm').submit(); }
  setInterval(poll, pollMs);
</script>
</@layout.registrationLayout>
