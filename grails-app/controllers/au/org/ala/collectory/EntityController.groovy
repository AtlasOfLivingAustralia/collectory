package au.org.ala.collectory

import au.org.ala.PermissionRequired

/**
 * This concrete class allows access to actions that are common to all sub-classes of ProviderGroup, and
 * which are defined in the abstract ProviderGroupController.
 */
@PermissionRequired(roles = ['ROLE_EDITOR', 'ROLE_ADMIN'])
class EntityController extends ProviderGroupController {

    def index = { }
    def providerGroupService

    protected ProviderGroup get(id) {
        return providerGroupService._get(id)
    }

}
