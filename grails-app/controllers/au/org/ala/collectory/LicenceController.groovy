package au.org.ala.collectory

import au.org.ala.plugins.openapi.Path

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import grails.converters.JSON
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.dao.DataIntegrityViolationException

import javax.ws.rs.Produces


/**
 * Simple webservice providing support licences in the system.
 */
class LicenceController {

    def collectoryAuthService
    @Operation(
            method = "GET",
            tags = "licence",
            operationId = "getLicence",
            summary = "Get a list of available licences",
            responses = [
                    @ApiResponse(
                            description = "A list of available licences",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            array = @ArraySchema(schema = @Schema(implementation =  LicenceResponse))
                                    )
                            ],
                            headers = [
                                    @Header(name = 'Access-Control-Allow-Headers', description = "CORS header", schema = @Schema(type = "String")),
                                    @Header(name = 'Access-Control-Allow-Methods', description = "CORS header", schema = @Schema(type = "String")),
                                    @Header(name = 'Access-Control-Allow-Origin', description = "CORS header", schema = @Schema(type = "String"))
                            ]
                    )
            ],
            security = []
    )
    @Path("/ws/licence")
    @Produces("application/json")
    def index() {
        response.setContentType("application/json")
        render (Licence.findAll().collect { [name:it.name, url:it.url] } as JSON)
    }

    @JsonIgnoreProperties('metaClass')
    class LicenceResponse {
        String name
        String url
    }



    def list() {
        if (params.message)
            flash.message = params.message
        params.max = Math.min(params.max ? params.int('max') : 50, 100)
        params.sort = params.sort ?: "name"
        [instanceList: Licence.list(params), entityType: 'Licence', instanceTotal: Licence.count()]
    }

    def create() {
        [licenceInstance: new Licence(params)]
    }

    def save() {
        def licenceInstance = new Licence(params)
        def savedInstance = null
        Licence.withTransaction {
            savedInstance = licenceInstance.save(flush: true)
        }

        if (!savedInstance) {
            render(view: "create", model: [licenceInstance: licenceInstance])
            return
        }

        flash.message = message(code: 'default.created.message', args: [message(code: 'licence.label', default: 'Licence'), licenceInstance.id])
        redirect(action: "show", id: licenceInstance.id)
    }

    def show(Long id) {
        def licenceInstance = Licence.get(id)
        if (!licenceInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'licence.label', default: 'Licence'), id])
            redirect(action: "list")
            return
        }

        [licenceInstance: licenceInstance]
    }

    def edit(Long id) {
        def licenceInstance = Licence.get(id)
        if (!licenceInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'licence.label', default: 'Licence'), id])
            redirect(action: "list")
            return
        }

        [licenceInstance: licenceInstance]
    }

    def update(Long id, Long version) {
        def licenceInstance = Licence.get(id)
        if (!licenceInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'licence.label', default: 'Licence'), id])
            redirect(action: "list")
            return
        }

        if (version != null) {
            if (licenceInstance.version > version) {
                licenceInstance.errors.rejectValue("version", "default.optimistic.locking.failure",
                        [message(code: 'licence.label', default: 'Licence')] as Object[],
                        "Another user has updated this Licence while you were editing")
                render(view: "edit", model: [licenceInstance: licenceInstance])
                return
            }
        }

        licenceInstance.properties = params

        def savedInstance = null
        Licence.withTransaction {
            savedInstance = licenceInstance.save(flush: true)
        }

        if (!savedInstance) {
            render(view: "edit", model: [licenceInstance: licenceInstance])
            return
        }

        flash.message = message(code: 'default.updated.message', args: [message(code: 'licence.label', default: 'Licence'), licenceInstance.id])
        redirect(action: "show", id: licenceInstance.id)
    }

    def delete(Long id) {
        if (collectoryAuthService?.userInRole(grailsApplication.config.ROLE_ADMIN)) {
            def licenceInstance = Licence.get(id)
            if (!licenceInstance) {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'licence.label', default: 'Licence'), id])
                redirect(action: "list")
                return
            }

            try {
                Licence.withTransaction {
                    licenceInstance.delete(flush: true)
                }
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'licence.label', default: 'Licence'), id])
                redirect(action: "list")
            }
            catch (DataIntegrityViolationException e) {
                flash.message = message(code: 'default.not.deleted.message', args: [message(code: 'licence.label', default: 'Licence'), id])
                redirect(action: "show", id: id)
            }
        } else{
            response.setHeader("Content-type", "text/plain; charset=UTF-8")
            render(message(code: "provider.group.controller.04", default: "You are not authorised to access this page."))
        }
    }
}
