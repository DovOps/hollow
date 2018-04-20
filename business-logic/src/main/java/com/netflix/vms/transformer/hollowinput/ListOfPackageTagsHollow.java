package com.netflix.vms.transformer.hollowinput;

import com.netflix.hollow.api.objects.HollowList;
import com.netflix.hollow.api.objects.delegate.HollowListDelegate;
import com.netflix.hollow.api.objects.generic.GenericHollowRecordHelper;

@SuppressWarnings("all")
public class ListOfPackageTagsHollow extends HollowList<StringHollow> {

    public ListOfPackageTagsHollow(HollowListDelegate delegate, int ordinal) {
        super(delegate, ordinal);
    }

    @Override
    public StringHollow instantiateElement(int ordinal) {
        return (StringHollow) api().getStringHollow(ordinal);
    }

    @Override
    public boolean equalsElement(int elementOrdinal, Object testObject) {
        return GenericHollowRecordHelper.equalObject(getSchema().getElementType(), elementOrdinal, testObject);
    }

    public VMSHollowInputAPI api() {
        return typeApi().getAPI();
    }

    public ListOfPackageTagsTypeAPI typeApi() {
        return (ListOfPackageTagsTypeAPI) delegate.getTypeAPI();
    }

}