package org.smartregister.reveal.interactor;

import android.support.annotation.VisibleForTesting;

import net.sqlcipher.Cursor;
import net.sqlcipher.SQLException;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteStatement;

import org.apache.commons.lang3.ArrayUtils;
import org.smartregister.clientandeventmodel.DateUtil;
import org.smartregister.cursoradapter.SmartRegisterQueryBuilder;
import org.smartregister.domain.db.Event;
import org.smartregister.family.util.DBConstants;
import org.smartregister.family.util.Utils;
import org.smartregister.repository.EventClientRepository;
import org.smartregister.repository.EventClientRepository.event_column;
import org.smartregister.repository.StructureRepository;
import org.smartregister.reveal.application.RevealApplication;
import org.smartregister.reveal.contract.StructureTasksContract;
import org.smartregister.reveal.model.EventTask;
import org.smartregister.reveal.model.StructureTaskDetails;
import org.smartregister.reveal.util.AppExecutors;
import org.smartregister.reveal.util.Constants;
import org.smartregister.reveal.util.Constants.Intervention;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

import static org.smartregister.domain.Task.TaskStatus.CANCELLED;
import static org.smartregister.domain.Task.TaskStatus.READY;
import static org.smartregister.family.util.DBConstants.KEY.DOB;
import static org.smartregister.family.util.DBConstants.KEY.FIRST_NAME;
import static org.smartregister.family.util.DBConstants.KEY.LAST_NAME;
import static org.smartregister.family.util.DBConstants.KEY.MIDDLE_NAME;
import static org.smartregister.reveal.util.Constants.DatabaseKeys.BUSINESS_STATUS;
import static org.smartregister.reveal.util.Constants.DatabaseKeys.CODE;
import static org.smartregister.reveal.util.Constants.DatabaseKeys.FOR;
import static org.smartregister.reveal.util.Constants.DatabaseKeys.GROUPID;
import static org.smartregister.reveal.util.Constants.DatabaseKeys.ID;
import static org.smartregister.reveal.util.Constants.DatabaseKeys.NAME;
import static org.smartregister.reveal.util.Constants.DatabaseKeys.PLAN_ID;
import static org.smartregister.reveal.util.Constants.DatabaseKeys.STATUS;
import static org.smartregister.reveal.util.Constants.DatabaseKeys.STRUCTURES_TABLE;
import static org.smartregister.reveal.util.Constants.DatabaseKeys.STRUCTURE_ID;
import static org.smartregister.reveal.util.Constants.DatabaseKeys.TASK_TABLE;
import static org.smartregister.reveal.util.FamilyConstants.TABLE_NAME.FAMILY_MEMBER;


/**
 * Created by samuelgithengi on 4/12/19.
 */
public class StructureTasksInteractor extends BaseInteractor implements StructureTasksContract.Interactor {

    private AppExecutors appExecutors;

    private SQLiteDatabase database;
    private StructureTasksContract.Presenter presenter;
    private StructureRepository structureRepository;

    public StructureTasksInteractor(StructureTasksContract.Presenter presenter) {
        this(presenter, RevealApplication.getInstance().getAppExecutors(), RevealApplication.getInstance().getRepository().getReadableDatabase(), RevealApplication.getInstance().getStructureRepository());
    }

    @VisibleForTesting
    protected StructureTasksInteractor(StructureTasksContract.Presenter presenter, AppExecutors appExecutors,
                                       SQLiteDatabase database, StructureRepository structureRepository) {
        super(presenter);
        this.presenter = presenter;
        this.appExecutors = appExecutors;
        this.database = database;
        this.structureRepository = structureRepository;
    }

    @Override
    public void findTasks(String structureId, String planId, String operationalAreaId) {
        appExecutors.diskIO().execute(() -> {
            List<StructureTaskDetails> taskDetailsList = new ArrayList<>();
            StructureTaskDetails incompleteIndexCase = null;
            Cursor cursor = null;
            try {
                cursor = database.rawQuery(getTaskSelect(String.format(
                        "%s=? AND %s=? AND %s != ?", FOR, PLAN_ID, STATUS)),
                        new String[]{structureId, planId, CANCELLED.name()});
                while (cursor.moveToNext()) {
                    taskDetailsList.add(readTaskDetails(cursor));
                }

                cursor.close();
                cursor = database.rawQuery(getMemberTasksSelect(String.format("%s.%s=? AND %s=? AND %s != ? AND %s IS NULL",
                        STRUCTURES_TABLE, ID, PLAN_ID, STATUS, DBConstants.KEY.DATE_REMOVED), getMemberColumns()),
                        new String[]{structureId, planId, CANCELLED.name()});
                while (cursor.moveToNext()) {
                    taskDetailsList.add(readMemberTaskDetails(cursor));
                }
                cursor.close();
                cursor = database.rawQuery(getTaskSelect(String.format("%s = ? AND %s = ? AND %s = ? AND %s = ?",
                        GROUPID, PLAN_ID, CODE, STATUS)),
                        new String[]{operationalAreaId, planId, Intervention.CASE_CONFIRMATION, READY.name()});
                if (cursor.moveToNext()) {
                    incompleteIndexCase = readTaskDetails(cursor);
                }
            } catch (Exception e) {
                Timber.e(e, "Error querying tasks for " + structureId);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            StructureTaskDetails finalIndexCase = incompleteIndexCase;
            populateEventsPerTask(taskDetailsList);
            appExecutors.mainThread().execute(() -> {
                presenter.onTasksFound(taskDetailsList, finalIndexCase);
            });
        });
    }

    @Override
    public void getStructure(StructureTaskDetails details) {
        appExecutors.diskIO().execute(() -> {

            String structureId = details.getTaskEntity();
            if (Intervention.PERSON_INTERVENTIONS.contains(details.getTaskCode())) {
                structureId = details.getStructureId();
            }
            org.smartregister.domain.Location structure =
                    structureRepository.getLocationById(structureId);
            appExecutors.mainThread().execute(() -> {
                presenter.onStructureFound(structure, details);
            });
        });
    }

    @Override
    public void findLastEvent(StructureTaskDetails taskDetails) {

        appExecutors.diskIO().execute(() -> {
            String eventType = taskDetails.getTaskCode().equals(Intervention.BLOOD_SCREENING) ? Constants.BLOOD_SCREENING_EVENT : Constants.BEDNET_DISTRIBUTION_EVENT;
            String events = String.format("select %s from %s where %s = ? and %s =? order by %s desc limit 1",
                    event_column.json, EventClientRepository.Table.event.name(), event_column.baseEntityId, event_column.eventType, event_column.updatedAt);
            Cursor cursor = null;
            try {
                cursor = database.rawQuery(events, new String[]{taskDetails.getTaskEntity(), eventType});
                if (cursor.moveToFirst()) {
                    String eventJSON = cursor.getString(0);
                    presenter.onEventFound(eventClientRepository.convert(eventJSON, Event.class));

                }
            } catch (SQLException e) {
                Timber.e(e);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        });

    }


    private void populateEventsPerTask(List<StructureTaskDetails> tasks) {
        SQLiteStatement eventsPerTask = database.compileStatement("SELECT count(*) as events_per_task FROM event_task WHERE task_id = ?");
        SQLiteStatement lastEventDate = database.compileStatement("SELECT max(event_date) FROM event_task WHERE task_id = ?");
        try {
            for (StructureTaskDetails task : tasks) {
                EventTask eventTask = new EventTask();

                eventsPerTask.bindString(1, task.getTaskId());
                eventTask.setEventsPerTask(eventsPerTask.simpleQueryForLong());

                if (eventTask.getEventsPerTask() > 1) {
                    lastEventDate.bindString(1, task.getTaskId());
                    eventTask.setLastEventDate(lastEventDate.simpleQueryForString());
                    task.setLastEdited(DateUtil.parseDate(eventTask.getLastEventDate()));
                }

            }

        } catch (Exception e) {
            Timber.e(e, "Error querying events counts ");
        } finally {
            if (eventsPerTask != null) {
                eventsPerTask.close();
            }
            if (lastEventDate != null) {
                lastEventDate.close();
            }
        }

    }


    private String getTaskSelect(String mainCondition) {
        SmartRegisterQueryBuilder queryBuilder = new SmartRegisterQueryBuilder();
        queryBuilder.selectInitiateMainTable(TASK_TABLE, getStructureColumns(), ID);
        return queryBuilder.mainCondition(mainCondition);
    }


    private String[] getStructureColumns() {
        return new String[]{
                TASK_TABLE + "." + ID,
                TASK_TABLE + "." + CODE,
                TASK_TABLE + "." + FOR,
                TASK_TABLE + "." + BUSINESS_STATUS,
                TASK_TABLE + "." + STATUS,
                TASK_TABLE + "." + STRUCTURE_ID
        };
    }

    private String[] getMemberColumns() {
        String[] columns = getStructureColumns();
        String[] otherColumns = new String[]{
                "printf('%s %s %s'," + FIRST_NAME + "," + MIDDLE_NAME + "," + LAST_NAME + ") AS " + NAME,
                DOB,
                FAMILY_MEMBER + "." + STRUCTURE_ID
        };
        return ArrayUtils.addAll(columns, otherColumns);
    }

    private StructureTaskDetails readTaskDetails(Cursor cursor) {
        StructureTaskDetails task = new StructureTaskDetails(cursor.getString(cursor.getColumnIndex(ID)));
        task.setTaskCode(cursor.getString(cursor.getColumnIndex(CODE)));
        task.setTaskEntity(cursor.getString(cursor.getColumnIndex(FOR)));
        task.setBusinessStatus(cursor.getString(cursor.getColumnIndex(BUSINESS_STATUS)));
        task.setTaskStatus(cursor.getString(cursor.getColumnIndex(STATUS)));
        task.setStructureId(cursor.getString(cursor.getColumnIndex(STRUCTURE_ID)));
        return task;
    }

    private StructureTaskDetails readMemberTaskDetails(Cursor cursor) {
        StructureTaskDetails task = readTaskDetails(cursor);
        String dob = cursor.getString(cursor.getColumnIndex(DOB));
        String dobString = Utils.getDuration(dob);
        dobString = dobString.contains("y") ? dobString.substring(0, dobString.indexOf("y")) : dobString;
        task.setTaskName(cursor.getString(cursor.getColumnIndex(NAME)) + ", " + dobString);
        task.setStructureId(cursor.getString(cursor.getColumnIndex(STRUCTURE_ID)));
        return task;
    }
}
