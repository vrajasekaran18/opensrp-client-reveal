package org.smartregister.reveal.fragment;

import android.content.Intent;
import android.view.View;

import org.apache.commons.lang3.StringUtils;
import org.smartregister.cursoradapter.RecyclerViewPaginatedAdapter;
import org.smartregister.reveal.R;
import org.smartregister.reveal.contract.EventRegisterContract;
import org.smartregister.reveal.model.TaskFilterParams;
import org.smartregister.reveal.presenter.EventRegisterFragmentPresenter;
import org.smartregister.reveal.util.Constants;
import org.smartregister.reveal.view.ListTasksActivity;
import org.smartregister.reveal.viewholder.EventViewHolder;
import org.smartregister.view.fragment.BaseRegisterFragment;

import java.util.HashMap;
import java.util.Set;

import static android.app.Activity.RESULT_OK;
import static org.smartregister.reveal.util.Constants.Filter.FILTER_SORT_PARAMS;

/**
 * Created by samuelgithengi on 7/30/20.
 */
public class EventRegisterFragment extends BaseRegisterFragment implements EventRegisterContract.View {

    @Override
    protected int getLayout() {
        return R.layout.fragment_event_register;
    }


    @Override
    protected void initializePresenter() {
        presenter = new EventRegisterFragmentPresenter(this, Constants.EventsRegister.VIEW_IDENTIFIER);
    }

    @Override
    public void initializeAdapter(Set<org.smartregister.configurableviews.model.View> visibleColumns) {
        EventViewHolder eventViewHolder = new EventViewHolder(getContext(), registerActionHandler, paginationViewHandler);
        clientAdapter = new RecyclerViewPaginatedAdapter(null, eventViewHolder, context().commonrepository(this.tablename));
        clientAdapter.setCurrentlimit(20);
        clientsView.setAdapter(clientAdapter);
    }

    @Override
    public void startMapActivity() {
        Intent intent = new Intent(getContext(), ListTasksActivity.class);
        getActivity().setResult(RESULT_OK, intent);
        getActivity().finish();
    }


    @Override
    public void setupViews(View view) {
        super.setupViews(view);
        view.findViewById(R.id.txt_map_label).setOnClickListener(v -> getPresenter().onOpenMapClicked());
    }

    @Override
    public void setUniqueID(String s) {//not used
    }


    @Override
    public void setAdvancedSearchFormData(HashMap<String, String> hashMap) {//not used
    }

    @Override
    protected String getMainCondition() {
        return "";
    }

    @Override
    protected String getDefaultSortQuery() {
        return Constants.DatabaseKeys.EVENT_DATE + " DESC";
    }

    @Override
    protected void startRegistration() {//not used
    }

    @Override
    protected void onViewClicked(View view) {
    }

    @Override
    public void showNotFoundPopup(String s) {//not used
    }

    EventRegisterContract.Presenter getPresenter() {
        return (EventRegisterContract.Presenter) presenter;
    }

}
