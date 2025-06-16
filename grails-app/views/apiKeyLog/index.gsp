<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="main"/>
    <title>Legacy apiKey requests</title>
    <meta name="layout" content="${grailsApplication.config.skin.layout}" />

</head>
<body>
<div class="container">
    <h1>Required ROLE Tests</h1>
    <ul>
        <li><g:link controller="apiKeyLog" action="roleTest" params="[dest: '/ws/contacts/1']">[Test] ROLE_ADMIN required by /data/contacts</g:link></li>
        <li><g:link controller="apiKeyLog" action="roleTest" params="[dest: '/ws/contacts/1', method: 'POST']">[Test] ROLE_EDITOR required</g:link></li>
    </ul>
    <h1>Legacy apiKey requests</h1>
    <table id="logTable" class="table table-striped table-bordered">
        <thead>
        <tr>
            <th>Remote Host</th>
            <th>Remote Addr</th>
            <th>Referer</th>
            <th>Forwarded For</th>
            <th>Controller</th>
            <th>Action</th>
            <th>Last Called</th>
        </tr>
        </thead>
        <tbody>
        <g:each in="${logs}" var="log">
            <tr>
                <td>${log.remoteHost}</td>
                <td>${log.remoteAddr}</td>
                <td>${log.remoteReferer}</td>
                <td>${log.remoteForwardedFor}</td>
                <td>${log.controllerName}</td>
                <td>${log.actionName}</td>
                <td><g:formatDate date="${log.lastCalled}" format="yyyy-MM-dd HH:mm:ss" /></td>
            </tr>
        </g:each>
        </tbody>
    </table>
</div>
</body>
</html>