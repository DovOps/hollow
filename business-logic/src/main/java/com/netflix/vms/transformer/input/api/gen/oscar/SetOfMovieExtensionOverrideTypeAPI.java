package com.netflix.vms.transformer.input.api.gen.oscar;

import com.netflix.hollow.api.custom.HollowSetTypeAPI;

import com.netflix.hollow.core.read.dataaccess.HollowSetTypeDataAccess;
import com.netflix.hollow.api.objects.delegate.HollowSetLookupDelegate;

@SuppressWarnings("all")
public class SetOfMovieExtensionOverrideTypeAPI extends HollowSetTypeAPI {

    private final HollowSetLookupDelegate delegateLookupImpl;

    public SetOfMovieExtensionOverrideTypeAPI(OscarAPI api, HollowSetTypeDataAccess dataAccess) {
        super(api, dataAccess);
        this.delegateLookupImpl = new HollowSetLookupDelegate(this);
    }

    public MovieExtensionOverrideTypeAPI getElementAPI() {
        return getAPI().getMovieExtensionOverrideTypeAPI();
    }

    public OscarAPI getAPI() {
        return (OscarAPI)api;
    }

    public HollowSetLookupDelegate getDelegateLookupImpl() {
        return delegateLookupImpl;
    }

}