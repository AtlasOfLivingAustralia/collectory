<%@ page import="grails.converters.JSON; au.org.ala.collectory.ProviderGroup; au.org.ala.collectory.Institution" %>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
        <meta name="layout" content="${grailsApplication.config.skin.layout}" />
        <title><g:message code="institution.base.label" default="Edit institution metadata" /></title>
        <asset:stylesheet src="application.css"/>
    </head>
    <body>
        <div class="nav">
        <h1><g:message code="collection.title.editing" />: ${command.name}</h1>
        </div>
        <div id="baseForm" class="body">
            <g:if test="${message}">
            <div class="message">${message}</div>
            </g:if>
            <g:hasErrors bean="${command}">
            <div class="errors">
                <g:renderErrors bean="${command}" as="list" />
            </div>
            </g:hasErrors>
            <g:form method="post" name="baseForm" action="base">
                <g:hiddenField name="id" value="${command?.id}" />
                <g:hiddenField name="uid" value="${command?.uid}" />
                <g:hiddenField name="version" value="${command.version}" />
                <!-- public description -->
                <div class="form-group">
                    <label for="pubDescription"><g:message code="providerGroup.pubDescription.label" default="Public Description" /> <cl:helpText code="institution.pubDescription"/></label>
                    <g:textArea name="pubDescription" class="form-control" cols="40" rows="${cl.textAreaHeight(text:command.pubDescription)}" value="${command.pubDescription}" />
                </div>

                <!-- tech description -->
                <div class="form-group">
                    <label for="techDescription"><g:message code="providerGroup.techDescription.label" default="Technical Description" /><cl:helpText code="institution.techDescription"/></label>
                    <g:textArea name="techDescription" class="form-control" cols="40" rows="${cl.textAreaHeight(text:command.techDescription)}" value="${command?.techDescription}" />
                </div>

                <!-- focus -->
                <div class="form-group">
                    <label for="focus"><g:message code="providerGroup.focus.label" default="Contribution" /><cl:helpText code="institution.focus"/></label>
                    <g:textArea name="focus" class="form-control" cols="40" rows="${cl.textAreaHeight(text:command.focus)}" value="${command?.focus}" />
                </div>

                <!-- type -->
                <div class="form-group">
                    <label for="institutionType"><g:message code="providerGroup.institutionType.label" default="Institution Type" /><cl:helpText code="collection.collectionType"/></label>
                    <g:select name="institutionType" class="form-control" from="${grailsApplication.config.institution.institutionTypes}" value="${command.institutionType}"/>
                </div>

                <div class="buttons">
                    <span class="button"><input type="submit" name="_action_updateDescription" value="${message(code:"collection.button.update")}" class="save btn btn-success"></span>
                    <span class="button"><input type="submit" name="_action_cancel" value="${message(code:"collection.button.cancel")}" class="cancel btn btn-default"></span>
                </div>
            </g:form>
        </div>
    </body>
</html>
