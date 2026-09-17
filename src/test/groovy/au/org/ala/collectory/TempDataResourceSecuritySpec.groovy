package au.org.ala.collectory

import au.org.ala.PermissionRequired
import au.org.ala.SkipPermissionCheck
import au.org.ala.grails.AnnotationMatcher
import grails.testing.gorm.DataTest
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

class TempDataResourceSecuritySpec extends Specification implements ControllerUnitTest<TempDataResourceController>, DataTest {

    def setupSpec() {
        mockDomains(TempDataResource)
    }

    def setup() {
        grailsApplication.addArtefact("Controller", TempDataResourceController)
        controller.crudService = Mock(CrudService)
    }

    def "getEntity action in TempDataResourceController is annotated with SkipPermissionCheck"() {
        expect:
        TempDataResourceController.getMethod("getEntity").isAnnotationPresent(SkipPermissionCheck)
    }

    def "PermissionInterceptor allows unauthenticated access to getEntity on TempDataResourceController"() {
        given:
        def matchResult = AnnotationMatcher.getAnnotation(
                grailsApplication,
                null,
                'tempDataResource',
                'getEntity',
                PermissionRequired,
                SkipPermissionCheck
        )

        expect:
        matchResult.controllerAnnotation != null
        matchResult.overrideAnnotation != null
        matchResult.effectiveAnnotation() != null && matchResult.overrideAnnotation != null
    }

    def "PermissionInterceptor requires permission for other actions on TempDataResourceController"() {
        given:
        def matchResult = AnnotationMatcher.getAnnotation(
                grailsApplication,
                null,
                'tempDataResource',
                'saveEntity',
                PermissionRequired,
                SkipPermissionCheck
        )

        expect:
        matchResult.controllerAnnotation != null
        matchResult.overrideAnnotation == null
    }

    def "getEntity returns list of tempDataResources as JSON without authentication"() {
        given:
        new TempDataResource(uid: 'drt1', name: 'Temp Resource 1').save(flush: true, failOnError: true)
        new TempDataResource(uid: 'drt2', name: 'Temp Resource 2').save(flush: true, failOnError: true)

        when:
        controller.getEntity()

        then:
        response.status == 200
        response.json.size() == 2
        response.json.collect { it.uid }.sort() == ['drt1', 'drt2']
    }

    def "getEntity returns single tempDataResource when drt is supplied"() {
        given:
        def drt = new TempDataResource(uid: 'drt1', name: 'Temp Resource 1').save(flush: true, failOnError: true)
        params.drt = drt
        1 * controller.crudService.readTempDataResource(drt) >> '{"uid":"drt1","name":"Temp Resource 1"}'

        when:
        controller.getEntity()

        then:
        response.status == 200
        response.text.contains('"uid":"drt1"')
    }
}
