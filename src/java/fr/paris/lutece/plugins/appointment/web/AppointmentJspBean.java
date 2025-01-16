/*
 * Copyright (c) 2002-2022, City of Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.plugins.appointment.web;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import fr.paris.lutece.api.user.User;
import fr.paris.lutece.plugins.appointment.business.appointment.Appointment;
import fr.paris.lutece.plugins.appointment.business.appointment.AppointmentSlot;
import fr.paris.lutece.plugins.appointment.business.form.Form;
import fr.paris.lutece.plugins.appointment.business.message.FormMessage;
import fr.paris.lutece.plugins.appointment.business.planning.WeekDefinition;
import fr.paris.lutece.plugins.appointment.business.rule.FormRule;
import fr.paris.lutece.plugins.appointment.business.rule.ReservationRule;
import fr.paris.lutece.plugins.appointment.business.slot.Slot;
import fr.paris.lutece.plugins.appointment.exception.AppointmentSavedException;
import fr.paris.lutece.plugins.appointment.exception.SlotEditTaskExpiredTimeException;
import fr.paris.lutece.plugins.appointment.exception.SlotFullException;
import fr.paris.lutece.plugins.appointment.log.LogUtilities;
import fr.paris.lutece.plugins.appointment.service.AppointmentResourceIdService;
import fr.paris.lutece.plugins.appointment.service.AppointmentResponseService;
import fr.paris.lutece.plugins.appointment.service.AppointmentService;
import fr.paris.lutece.plugins.appointment.service.AppointmentUtilities;
import fr.paris.lutece.plugins.appointment.service.CommentService;
import fr.paris.lutece.plugins.appointment.service.EntryService;
import fr.paris.lutece.plugins.appointment.service.FormMessageService;
import fr.paris.lutece.plugins.appointment.service.FormRuleService;
import fr.paris.lutece.plugins.appointment.service.FormService;
import fr.paris.lutece.plugins.appointment.service.ReservationRuleService;
import fr.paris.lutece.plugins.appointment.service.SlotSafeService;
import fr.paris.lutece.plugins.appointment.service.SlotService;
import fr.paris.lutece.plugins.appointment.service.Utilities;
import fr.paris.lutece.plugins.appointment.service.WeekDefinitionService;
import fr.paris.lutece.plugins.appointment.service.addon.AppointmentAddOnManager;
import fr.paris.lutece.plugins.appointment.service.export.AppointmentExportService;
import fr.paris.lutece.plugins.appointment.service.export.ExcelAppointmentGenerator;
import fr.paris.lutece.plugins.appointment.service.listeners.AppointmentListenerManager;
import fr.paris.lutece.plugins.appointment.service.upload.AppointmentAsynchronousUploadHandler;
import fr.paris.lutece.plugins.appointment.web.dto.AppointmentDTO;
import fr.paris.lutece.plugins.appointment.web.dto.AppointmentFilterDTO;
import fr.paris.lutece.plugins.appointment.web.dto.AppointmentFormDTO;
import fr.paris.lutece.plugins.filegenerator.service.TemporaryFileGeneratorService;
import fr.paris.lutece.plugins.genericattributes.business.Entry;
import fr.paris.lutece.plugins.genericattributes.business.EntryHome;
import fr.paris.lutece.plugins.genericattributes.business.GenericAttributeError;
import fr.paris.lutece.plugins.genericattributes.business.Response;
import fr.paris.lutece.plugins.genericattributes.business.ResponseHome;
import fr.paris.lutece.plugins.genericattributes.service.file.GenericAttributeFileService;
import fr.paris.lutece.plugins.workflowcore.business.state.State;
import fr.paris.lutece.plugins.workflowcore.business.state.StateFilter;
import fr.paris.lutece.plugins.workflowcore.service.state.StateService;
import fr.paris.lutece.plugins.workflowcore.service.task.ITask;
import fr.paris.lutece.plugins.workflowcore.service.task.ITaskService;
import fr.paris.lutece.plugins.workflowcore.service.task.TaskService;
import fr.paris.lutece.portal.business.file.File;
import fr.paris.lutece.portal.business.user.AdminUser;
import fr.paris.lutece.portal.business.user.AdminUserHome;
import fr.paris.lutece.portal.service.admin.AccessDeniedException;
import fr.paris.lutece.portal.service.admin.AdminAuthenticationService;
import fr.paris.lutece.portal.service.i18n.I18nService;
import fr.paris.lutece.portal.service.mailinglist.AdminMailingListService;
import fr.paris.lutece.portal.service.message.AdminMessage;
import fr.paris.lutece.portal.service.message.AdminMessageService;
import fr.paris.lutece.portal.service.plugin.Plugin;
import fr.paris.lutece.portal.service.plugin.PluginService;
import fr.paris.lutece.portal.service.rbac.RBACService;
import fr.paris.lutece.portal.service.spring.SpringContextService;
import fr.paris.lutece.portal.service.template.AppTemplateService;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPathService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import fr.paris.lutece.portal.service.workflow.WorkflowService;
import fr.paris.lutece.portal.util.mvc.admin.MVCAdminJspBean;
import fr.paris.lutece.portal.util.mvc.admin.annotations.Controller;
import fr.paris.lutece.portal.util.mvc.commons.annotations.Action;
import fr.paris.lutece.portal.util.mvc.commons.annotations.View;
import fr.paris.lutece.portal.util.mvc.utils.MVCUtils;
import fr.paris.lutece.portal.web.util.LocalizedPaginator;
import fr.paris.lutece.util.ReferenceList;
import fr.paris.lutece.util.html.AbstractPaginator;
import fr.paris.lutece.util.html.HtmlTemplate;
import fr.paris.lutece.util.url.UrlItem;

/**
 * This class provides the user interface to manage Appointment features ( manage, create, modify, remove )
 *
 * @author Laurent Payen
 *
 */
@Controller( controllerJsp = "ManageAppointments.jsp", controllerPath = "jsp/admin/plugins/appointment/", right = AppointmentFormJspBean.RIGHT_MANAGEAPPOINTMENTFORM )
public class AppointmentJspBean extends MVCAdminJspBean
{
    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 1978001810468444844L;
    private static final String PARAMETER_PAGE_INDEX = "page_index";

    // //////////////////////////////////////////////////////////////////////////
    // Constants

    // templates
    private static final String TEMPLATE_MANAGE_APPOINTMENTS_CALENDAR = "/admin/plugins/appointment/appointment/manage_appointments_calendar.html";
    private static final String TEMPLATE_MANAGE_APPOINTMENTS_CALENDAR_MULTI_SLOT = "/admin/plugins/appointment/appointment/manage_appointments_multislot_calendar.html";
    private static final String TEMPLATE_MANAGE_APPOINTMENTS_CALENDAR_GROUPED = "/admin/plugins/appointment/appointment/appointment_form_list_open_slots_grouped.html";
    private static final String TEMPLATE_CREATE_APPOINTMENT = "/admin/plugins/appointment/appointment/create_appointment.html";
    private static final String TEMPLATE_MANAGE_APPOINTMENTS = "/admin/plugins/appointment/appointment/manage_appointments.html";
    private static final String TEMPLATE_VIEW_APPOINTMENT = "/admin/plugins/appointment/appointment/view_appointment.html";
    private static final String TEMPLATE_HTML_CODE_FORM_ADMIN = "admin/plugins/appointment/html_code_form.html";
    private static final String TEMPLATE_APPOINTMENT_FORM_RECAP = "/admin/plugins/appointment/appointment/appointment_form_recap.html";
    private static final String TEMPLATE_TASKS_FORM_WORKFLOW = "admin/plugins/appointment/appointment/tasks_form_workflow.html";

    // Properties for page titles
    private static final String PROPERTY_PAGE_TITLE_MANAGE_APPOINTMENTS = "appointment.manageAppointments.pageTitle";
    private static final String PROPERTY_PAGE_TITLE_MANAGE_APPOINTMENTS_CALENDAR = "appointment.manageAppointmentCalendar.pageTitle";
    private static final String PROPERTY_PAGE_TITLE_CREATE_APPOINTMENT = "appointment.name.create";
    private static final String PROPERTY_PAGE_TITLE_VIEW_APPOINTMENT = "appointment.viewAppointment.pageTitle";
    private static final String PROPERTY_PAGE_TITLE_RECAP_APPOINTMENT = "appointment.appointmentApp.recap.title";
    private static final String PROPERTY_PAGE_TITLE_TASKS_FORM_WORKFLOW = "appointment.taskFormWorkflow.pageTitle";

    private static final String UNRESERVED = "appointment.message.labelStatusUnreserved";
    private static final String RESERVED = "appointment.message.labelStatusReserved";

    // Parameters
    private static final String PARAMETER_ID_RESPONSE = "idResponse";
    private static final String PARAMETER_STARTING_DATE_TIME = "starting_date_time";
    private static final String PARAMETER_ENDING_DATE_TIME = "ending_date_time";
    private static final String PARAMETER_STARTING_DATE_OF_DISPLAY = "starting_date_of_display";
    private static final String PARAMETER_STR_STARTING_DATE_OF_DISPLAY = "str_starting_date_of_display";
    private static final String PARAMETER_ENDING_DATE_OF_DISPLAY = "ending_date_of_display";
    private static final String PARAMETER_STR_ENDING_DATE_OF_DISPLAY = "str_ending_date_of_display";
    private static final String PARAMETER_DATE_OF_DISPLAY = "date_of_display";
    private static final String PARAMETER_DAY_OF_WEEK = "dow";
    private static final String PARAMETER_EVENTS = "events";
    private static final String PARAMETER_EVENTS_COMMENTS = "comment_events";
    private static final String PARAMETER_MIN_DURATION = "min_duration";
    private static final String PARAMETER_MIN_TIME = "min_time";
    private static final String PARAMETER_MAX_TIME = "max_time";
    private static final String PARAMETER_ID_APPOINTMENT = "id_appointment";
    private static final String PARAMETER_ID_ACTION = "id_action";
    private static final String PARAMETER_ID_FORM = "id_form";
    private static final String PARAMETER_COME_FROM_CALENDAR = "comeFromCalendar";
    private static final String PARAMETER_EMAIL = "email";
    private static final String PARAMETER_EMAIL_CONFIRMATION = "emailConfirm";
    private static final String PARAMETER_FIRST_NAME = "firstname";
    private static final String PARAMETER_LAST_NAME = "lastname";
    private static final String PARAMETER_BACK = "back";
    private static final String PARAMETER_ORDER_BY = "orderBy";
    private static final String PARAMETER_ORDER_ASC = "orderAsc";
    private static final String PARAMETER_ID_APPOINTMENT_DELETE = "apmt";
    private static final String PARAMETER_DELETE_AND_BACK = "eraseAll";
    private static final String PARAMETER_SEARCH = "Search";
    private static final String PARAMETER_RESET = "reset";
    private static final String PARAMETER_NUMBER_OF_BOOKED_SEATS = "nbBookedSeats";
    private static final String PARAMETER_STATUS_CANCELLED = "status_cancelled";
    private static final String PARAMETER_MODIF_DATE = "modif_date";
    private static final String PARAMETER_IS_MODIFICATION = "is_modification";
    private static final String PARAMETER_NB_PLACE_TO_TAKE = "nbPlacesToTake";
    private static final String PARAMETER_SELECTED_DEFAULT_FIELD = "selectedDefaultFieldList";
    private static final String PARAMETER_SELECTED_CUSTOM_FIELD = "selectedCustomFieldList";

    // Markers
    private static final String MARK_TASKS_FORM = "tasks_form";
    private static final String MARK_APPOINTMENT_LIST = "appointment_list";
    private static final String MARK_APPOINTMENT = "appointment";
    private static final String MARK_PAGINATOR = "paginator";
    private static final String MARK_NB_ITEMS_PER_PAGE = "nb_items_per_page";
    private static final String MARK_FORM_MESSAGES = "formMessages";
    private static final String MARK_FORM_HTML = "form_html";
    private static final String MARK_FORM = "form";
    private static final String MARK_MODIFICATION_DATE_APPOINTMENT = "modifDateAppointment";
    private static final String MARK_FORM_CALENDAR_ERRORS = "formCalendarErrors";
    private static final String MARK_FORM_ERRORS = "form_errors";
    private static final String MARK_LIST_ERRORS = "listAllErrors";
    private static final String MARK_LOCALE = "locale";
    private static final String MARK_PLACES = "nbplaces";
    private static final String MARK_STR_ENTRY = "str_entry";
    private static final String MARK_RIGHT_CREATE = "rightCreate";
    private static final String MARK_RIGHT_DELETE = "rightDelete";
    private static final String MARK_RIGHT_VIEW = "rightView";
    private static final String MARK_RIGHT_CHANGE_STATUS = "rightChangeStatus";
    private static final String MARK_FILTER = "filter";
    private static final String MARK_LIST_STATUS = "listStatus";
    private static final String MARK_RESOURCE_HISTORY = "resource_history";
    private static final String MARK_ADDON = "addon";
    private static final String MARK_LIST_RESPONSE_RECAP_DTO = "listResponseRecapDTO";
    private static final String MARK_LANGUAGE = "language";
    private static final String MARK_ACTIVATE_WORKFLOW = "activateWorkflow";
    private static final String MARK_FORM_OVERBOOKING_ALLOWED = "overbookingAllowed";
    private static final String MARK_DEFAULT_FIELD_LIST = "defaultFieldList";
    private static final String MARK_CUSTOM_FIELD_LIST = "customFieldList";
    private static final String MARK_IS_OVERBOOKING = "isOverbooking";
    private static final String MARK_MAILING_LIST = "mailing_list";
    private static final String MARK_USER_CREATOR = "userCreator";
    private static final String JSP_MANAGE_APPOINTMENTS = "jsp/admin/plugins/appointment/ManageAppointments.jsp";
    private static final String ERROR_MESSAGE_SLOT_FULL = "appointment.message.error.slotFull";
    private static final String ERROR_MESSAGE_SLOT_EDIT_TASK_EXPIRED_TIME = "appointment.message.error.appointment.edit.expired.time";
    private static final String MARK_APPOINTMENT_DESK_ENABLED = "isDeskInstalled";

    // Messages
    private static final String MESSAGE_CONFIRM_REMOVE_APPOINTMENT = "appointment.message.confirmRemoveAppointment";
    private static final String MESSAGE_CONFIRM_REMOVE_MASSAPPOINTMENT = "appointment.message.confirmRemoveMassAppointment";
    private static final String ERROR_MESSAGE_NB_MAX_APPOINTMENTS_ON_A_CATEGORY = "appointment.validation.appointment.NbMaxAppointmentsOnCategory.error";
    private static final String ERROR_MESSAGE_NB_MIN_DAYS_BETWEEN_TWO_APPOINTMENTS = "appointment.validation.appointment.NbMinDaysBetweenTwoAppointments.error";
    private static final String ERROR_MESSAGE_NB_MAX_APPOINTMENTS_ON_A_PERIOD = "appointment.validation.appointment.NbMaxAppointmentsOnAPeriod.error";

    // Properties
    private static final String PROPERTY_DEFAULT_LIST_APPOINTMENT_PER_PAGE = "appointment.listAppointments.itemsPerPage";
    private static final String PROPERTY_NB_WEEKS_TO_DISPLAY_IN_BO = "appointment.nbWeeksToDisplayInBO";
    private static final String PROPERTY_MODULE_APPOINTMENT_DESK_NAME = "appointment.moduleAppointmentDesk.name";

    // Views
    private static final String VIEW_MANAGE_APPOINTMENTS = "manageAppointments";
    private static final String VIEW_CREATE_APPOINTMENT = "createAppointment";
    private static final String VIEW_VIEW_APPOINTMENT = "viewAppointment";
    private static final String VIEW_DISPLAY_RECAP_APPOINTMENT = "displayRecapAppointment";
    private static final String VIEW_CALENDAR_MANAGE_APPOINTMENTS = "viewCalendarManageAppointment";
    private static final String VIEW_WORKFLOW_ACTION_FORM = "viewWorkflowActionForm";
    private static final String VIEW_CHANGE_DATE_APPOINTMENT = "viewChangeDateAppointment";

    // Actions
    private static final String ACTION_DO_VALIDATE_FORM = "doValidateForm";
    private static final String ACTION_REMOVE_APPOINTMENT = "removeAppointment";
    private static final String ACTION_REMOVE_MASSAPPOINTMENT = "removeMassAppointment";
    private static final String ACTION_CONFIRM_REMOVE_APPOINTMENT = "confirmRemoveAppointment";
    private static final String ACTION_CONFIRM_REMOVE_MASS_APPOINTMENT = "confirmRemoveMassAppointment";
    private static final String ACTION_DO_MAKE_APPOINTMENT = "doMakeAppointment";
    private static final String ACTION_DO_PROCESS_WORKFLOW_ACTION = "doProcessWorkflowAction";
    private static final String ACTION_DO_CHANGE_APPOINTMENT_STATUS = "doChangeAppointmentStatus";
    private static final String ACTION_EXPORT_APPOINTMENTS = "doExportAppointments";

    // Infos
    private static final String INFO_APPOINTMENT_CREATED = "appointment.info.appointment.created";
    private static final String INFO_APPOINTMENT_REMOVED = "appointment.info.appointment.removed";
    private static final String INFO_APPOINTMENT_MASSREMOVED = "appointment.info.appointment.removed";

    // Error
    private static final String ERROR_MESSAGE_FORM_NOT_ACTIVE = "appointment.validation.appointment.formNotActive";
    private static final String ERROR_MESSAGE_NO_STARTING_VALIDITY_DATE = "appointment.validation.appointment.noStartingValidityDate";
    private static final String ERROR_MESSAGE_FORM_NO_MORE_VALID = "appointment.validation.appointment.formNoMoreValid";
    private static final String MESSAGE_UNVAILABLE_SLOT = "appointment.slot.unvailable";
    private static final String ERROR_MESSAGE_REPORT_APPOINTMENT = "appointment.message.error.report.appointment";

    // Constants
    private static final String DEFAULT_CURRENT_PAGE = "1";
    public static final String ACTIVATEWORKFLOW = AppPropertiesService.getProperty( "appointment.activate.workflow" );
    public static final String PREVIOUS_FORM = "calendar";
    private static final String LAST_NAME = "last_name";
    private static final String FIRST_NAME = "first_name";
    private static final String EMAIL = "email";
    private static final String PHONE_NUMBER = "phone_number";
    private static final String NB_BOOKED_SEATS = "nbBookedSeats";
    private static final String DATE_APPOINTMENT = "date_appointment";
    private static final String ADMIN = "admin";
    private static final String STATUS = "status";
    // services

    // Session variable to store working values
    private String _strCurrentPageIndex;
    private int _nItemsPerPage;
    private int _nDefaultItemsPerPage;
    private int _nNbPlacesToTake;
    private AppointmentFilterDTO _filter;
    private AppointmentFormDTO _appointmentForm;
    private AppointmentDTO _notValidatedAppointment;
    private AppointmentDTO _validatedAppointment;
    private List<Integer> _listAppointmentsIds;
    List<GenericAttributeError> listFormErrors = new ArrayList<>( );
    Plugin _moduleAppointmentDesk = PluginService.getPlugin( AppPropertiesService.getProperty( PROPERTY_MODULE_APPOINTMENT_DESK_NAME ) );

    /**
     * Get the page to manage appointments. Appointments are displayed in a calendar.
     *
     * @param request
     *            The request
     * @return The HTML code to display
     * @throws AccessDeniedException
     */
    @View( value = VIEW_CALENDAR_MANAGE_APPOINTMENTS, defaultView = true )
    public synchronized String getViewCalendarManageAppointments( HttpServletRequest request ) throws AccessDeniedException
    {
        String strIdForm = request.getParameter( PARAMETER_ID_FORM );

        if ( !RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, strIdForm, AppointmentResourceIdService.PERMISSION_VIEW_FORM, (User) getUser( ) ) )
        {
            throw new AccessDeniedException( AppointmentResourceIdService.PERMISSION_VIEW_FORM );
        }

        cleanSession( request.getSession( ) );
        String strIdAppointment = request.getParameter( PARAMETER_ID_APPOINTMENT );
        String nbPlacesToTake = request.getParameter( PARAMETER_NB_PLACE_TO_TAKE );
        boolean bError = false;

        if ( StringUtils.isNotEmpty( strIdAppointment ) )
        {
            // If we want to change the date of an appointment
            AppointmentDTO appointmentDTO = AppointmentService.buildAppointmentDTOFromIdAppointment( Integer.parseInt( strIdAppointment ) );
            if ( appointmentDTO.getIsCancelled( ) )
            {
                addError( ERROR_MESSAGE_REPORT_APPOINTMENT, getLocale( ) );
                bError = true;
            }
            else
            {
                _validatedAppointment = appointmentDTO;
                AppointmentService.addAppointmentResponses( _validatedAppointment );
                nbPlacesToTake = Integer.toString( _validatedAppointment.getNbBookedSeats( ) );
            }
        }
        int nIdForm = Integer.parseInt( strIdForm );
        AppointmentFormDTO appointmentForm = FormService.buildAppointmentFormWithoutReservationRule( nIdForm );

        if ( !appointmentForm.getIsActive( ) )
        {
            addError( ERROR_MESSAGE_FORM_NOT_ACTIVE, getLocale( ) );
            bError = true;
        }
        // Check if the date of display and the endDateOfDisplay are in the
        // validity date range of the form
        if ( appointmentForm.getDateStartValidity( ) == null )
        {
            addError( ERROR_MESSAGE_NO_STARTING_VALIDITY_DATE, getLocale( ) );
            bError = true;
        }

        int nNbWeeksToDisplay = AppPropertiesService.getPropertyInt( PROPERTY_NB_WEEKS_TO_DISPLAY_IN_BO, appointmentForm.getNbWeeksToDisplay( ) );
        LocalDate startingDateOfDisplay = LocalDate.now( ).minusWeeks( nNbWeeksToDisplay );
        LocalDate endingDateOfDisplay = LocalDate.now( ).plusWeeks( nNbWeeksToDisplay );
        if ( appointmentForm.getDateEndValidity( ) != null )
        {
            if ( startingDateOfDisplay.isAfter( endingDateOfDisplay ) )
            {
                addError( ERROR_MESSAGE_FORM_NO_MORE_VALID, getLocale( ) );
                bError = true;
            }
            if ( endingDateOfDisplay.isAfter( appointmentForm.getDateEndValidity( ).toLocalDate( ) ) )
            {
                endingDateOfDisplay = appointmentForm.getDateEndValidity( ).toLocalDate( );
            }
        }
        String strDateOfDisplay = request.getParameter( PARAMETER_DATE_OF_DISPLAY );
        LocalDate dateOfDisplay = LocalDate.now( );
        if ( StringUtils.isNotEmpty( strDateOfDisplay ) )
        {
            dateOfDisplay = LocalDate.parse( strDateOfDisplay );
        }
        List<Slot> listSlot = new ArrayList<>( );
        List<WeekDefinition> listWeekDefinition = WeekDefinitionService.findListWeekDefinition( nIdForm );
        Map<WeekDefinition, ReservationRule> mapReservationRule = ReservationRuleService.findAllReservationRule( nIdForm, listWeekDefinition );
        List<ReservationRule> listReservationRules = new ArrayList<>( mapReservationRule.values( ) );

        LocalTime maxEndingTime = WeekDefinitionService.getMaxEndingTimeOfAListOfWeekDefinition( listReservationRules );
        LocalTime minStartingTime = WeekDefinitionService.getMinStartingTimeOfAListOfWeekDefinition( listReservationRules );
        List<String> listDayOfWeek = new ArrayList<>( WeekDefinitionService.getSetDaysOfWeekOfAListOfWeekDefinitionForFullCalendar( listReservationRules ) );
        if ( !bError )
        {

            boolean isNewNbPlacesToTake = ( nbPlacesToTake != null && StringUtils.isNumeric( nbPlacesToTake ) );
            if ( appointmentForm.getIsMultislotAppointment( ) && ( ( _nNbPlacesToTake != 0 || isNewNbPlacesToTake ) && nbPlacesToTake != null ) )
            {

                _nNbPlacesToTake = isNewNbPlacesToTake ? Integer.parseInt( nbPlacesToTake ) : _nNbPlacesToTake;
                listSlot = SlotService.buildListSlot( nIdForm, mapReservationRule, startingDateOfDisplay, endingDateOfDisplay, _nNbPlacesToTake,
                        appointmentForm.getBoOverbooking( ) && RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, strIdForm,
                                AppointmentResourceIdService.PERMISSION_OVERBOOKING_FORM, (User) getUser( ) ) );

            }
            else
            {

                _nNbPlacesToTake = 0;
                listSlot = SlotService.buildListSlot( nIdForm, mapReservationRule, startingDateOfDisplay, endingDateOfDisplay );
            }

            // Tag as passed the slots passed
            List<Slot> listSlotsPassed = listSlot.stream( ).filter( s -> s.getEndingDateTime( ).isBefore( LocalDateTime.now( ) ) )
                    .collect( Collectors.toList( ) );
            for ( Slot slotPassed : listSlotsPassed )
            {
                slotPassed.setIsPassed( Boolean.TRUE );
            }
        }
        Map<String, Object> model = getModel( );
        if ( bError )
        {
            model.put( MARK_FORM_CALENDAR_ERRORS, bError );
        }
        // If we change the date of an appointment
        // filter the list of slot with only the ones that have enough places at
        // the moment of the edition
        if ( _validatedAppointment != null )
        {
            int nbBookedSeats = _validatedAppointment.getNbBookedSeats( );
            listSlot = listSlot.stream( ).filter( s -> s.getNbPotentialRemainingPlaces( ) >= nbBookedSeats && s.getIsOpen( ) ).collect( Collectors.toList( ) );
            model.put( MARK_MODIFICATION_DATE_APPOINTMENT, true );
        }
        else
        {
            model.put( MARK_MODIFICATION_DATE_APPOINTMENT, false );
        }

        model.put( MARK_FORM, appointmentForm );
        model.put( PARAMETER_ID_FORM, nIdForm );
        model.put( MARK_FORM_MESSAGES, FormMessageService.findFormMessageByIdForm( nIdForm ) );
        model.put( PARAMETER_STARTING_DATE_OF_DISPLAY, startingDateOfDisplay );
        model.put( PARAMETER_STR_STARTING_DATE_OF_DISPLAY, startingDateOfDisplay.format( Utilities.getFormatter( ) ) );
        model.put( PARAMETER_ENDING_DATE_OF_DISPLAY, endingDateOfDisplay );
        model.put( PARAMETER_STR_ENDING_DATE_OF_DISPLAY, endingDateOfDisplay.format( Utilities.getFormatter( ) ) );
        model.put( PARAMETER_DATE_OF_DISPLAY, dateOfDisplay );
        model.put( PARAMETER_DAY_OF_WEEK, listDayOfWeek );
        model.put( PARAMETER_EVENTS, listSlot );
        model.put( PARAMETER_EVENTS_COMMENTS, CommentService
                .buildCommentDTO( CommentService.finListComments( Date.valueOf( startingDateOfDisplay ), Date.valueOf( endingDateOfDisplay ), nIdForm ) ) );
        model.put( PARAMETER_MIN_TIME, minStartingTime );
        model.put( PARAMETER_MAX_TIME, maxEndingTime );
        model.put( PARAMETER_MIN_DURATION, LocalTime.MIN.plusMinutes( AppointmentUtilities.THIRTY_MINUTES ) );
        model.put( MARK_FORM_OVERBOOKING_ALLOWED, appointmentForm.getBoOverbooking( ) && RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, strIdForm,

                AppointmentResourceIdService.PERMISSION_OVERBOOKING_FORM, (User) getUser( ) ) );
        model.put( MARK_LOCALE, getLocale( ) );
        model.put( MARK_MAILING_LIST, AdminMailingListService.getMailingLists( getUser( ) ) );
        model.put( AppointmentUtilities.MARK_PERMISSION_ADD_COMMENT, String.valueOf( RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE,
                String.valueOf( appointmentForm.getIdForm( ) ), AppointmentResourceIdService.PERMISSION_ADD_COMMENT_FORM, (User) getUser( ) ) ) );
        model.put( AppointmentUtilities.MARK_PERMISSION_MODERATE_COMMENT, String.valueOf( RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE,
                String.valueOf( appointmentForm.getIdForm( ) ), AppointmentResourceIdService.PERMISSION_MODERATE_COMMENT_FORM, (User) getUser( ) ) ) );
        model.put( AppointmentUtilities.MARK_PERMISSION_ACCESS_CODE, getUser( ).getAccessCode( ) );
        model.put( MARK_APPOINTMENT_DESK_ENABLED, ( _moduleAppointmentDesk != null && _moduleAppointmentDesk.isInstalled( ) ) );

        if ( appointmentForm.getIsMultislotAppointment( ) && _nNbPlacesToTake <= 0 )
        {

            return getPage( PROPERTY_PAGE_TITLE_MANAGE_APPOINTMENTS_CALENDAR, TEMPLATE_MANAGE_APPOINTMENTS_CALENDAR_MULTI_SLOT, model );

        }
        else
            if ( appointmentForm.getIsMultislotAppointment( ) && _nNbPlacesToTake >= 1 )
            {

                return getPage( PROPERTY_PAGE_TITLE_MANAGE_APPOINTMENTS_CALENDAR, TEMPLATE_MANAGE_APPOINTMENTS_CALENDAR_GROUPED, model );

            }
            else
            {

                return getPage( PROPERTY_PAGE_TITLE_MANAGE_APPOINTMENTS_CALENDAR, TEMPLATE_MANAGE_APPOINTMENTS_CALENDAR, model );
            }
    }

    /**
     * Get the page to manage appointments
     *
     * @param request
     *            The request
     * @return The HTML code to display
     * @throws AccessDeniedException
     */
    @View( value = VIEW_MANAGE_APPOINTMENTS )
    public synchronized String getManageAppointments( HttpServletRequest request ) throws AccessDeniedException
    {
        String strIdForm = request.getParameter( PARAMETER_ID_FORM );

        if ( !RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, strIdForm, AppointmentResourceIdService.PERMISSION_VIEW_FORM, (User) getUser( ) ) )
        {
            throw new AccessDeniedException( AppointmentResourceIdService.PERMISSION_VIEW_FORM );
        }
        String strModifDateAppointment = request.getParameter( PARAMETER_MODIF_DATE );
        if ( strModifDateAppointment != null && Boolean.parseBoolean( strModifDateAppointment ) )
        {
            return getViewChangeDateAppointment( request );
        }
        // Clean session
        AppointmentAsynchronousUploadHandler.getHandler( ).removeSessionFiles( request.getSession( ) );
        _notValidatedAppointment = null;
        _validatedAppointment = null;
        _appointmentForm = null;
        int nIdForm = Integer.parseInt( strIdForm );

        // If it is a new search
        if ( request.getParameter( PARAMETER_SEARCH ) != null )
        {
            // Populate the filter
            populate( _filter, request );
        }
        else
            if ( request.getParameter( PARAMETER_RESET ) != null || _filter == null || _filter.getIdForm( ) != nIdForm )
            {
                _filter = new AppointmentFilterDTO( );
                _filter.setIdForm( nIdForm );
                // if we come from the calendar, need to get the starting and ending
                // time of the slot
                String strStartingDateTime = request.getParameter( PARAMETER_STARTING_DATE_TIME );
                String strEndingDateTime = request.getParameter( PARAMETER_ENDING_DATE_TIME );
                if ( strStartingDateTime != null && strEndingDateTime != null )
                {
                    LocalDateTime startingDateTime = LocalDateTime.parse( strStartingDateTime );
                    LocalDateTime endingDateTime = LocalDateTime.parse( strEndingDateTime );
                    _filter.setStartingDateOfSearch( Date.valueOf( startingDateTime.toLocalDate( ) ) );
                    _filter.setStartingTimeOfSearch( startingDateTime.toLocalTime( ).toString( ) );
                    _filter.setEndingDateOfSearch( Date.valueOf( endingDateTime.toLocalDate( ) ) );
                    _filter.setEndingTimeOfSearch( endingDateTime.toLocalTime( ).toString( ) );
                }
            }
        _strCurrentPageIndex = AbstractPaginator.getPageIndex( request, AbstractPaginator.PARAMETER_PAGE_INDEX, _strCurrentPageIndex );
        String strOrderBy = request.getParameter( PARAMETER_ORDER_BY );
        String strOrderAsc = request.getParameter( PARAMETER_ORDER_ASC );
        if ( strOrderBy == null )
        {
            strOrderBy = DATE_APPOINTMENT;
        }

        boolean bAsc = Boolean.FALSE;
        if ( strOrderAsc != null )
        {
            bAsc = Boolean.parseBoolean( strOrderAsc );
        }
        _filter.setOrderBy( strOrderBy );
        _filter.setOrderAsc( bAsc );


        if ( _strCurrentPageIndex == null )
        {
            _strCurrentPageIndex = DEFAULT_CURRENT_PAGE;
        }
        if ( DEFAULT_CURRENT_PAGE.equals( _strCurrentPageIndex ) )
        {
            _listAppointmentsIds = AppointmentService.findListAppointmentsIdsByFilter( _filter );
        }
        _nItemsPerPage = AbstractPaginator.getItemsPerPage( request, AbstractPaginator.PARAMETER_ITEMS_PER_PAGE, _nItemsPerPage, _nDefaultItemsPerPage );
        List<AppointmentDTO> listAppointmentsDTO = findListAppointmentsDTOByFilterByPage( );
        // If it is an order by
        listAppointmentsDTO = orderList( listAppointmentsDTO );
        if ( StringUtils.isNotEmpty( request.getParameter( PARAMETER_DELETE_AND_BACK ) ) )
        {
            String [ ] tabIdAppointmentToDelete = request.getParameterValues( PARAMETER_ID_APPOINTMENT_DELETE );
            if ( tabIdAppointmentToDelete != null )
            {
                request.getSession( ).setAttribute( PARAMETER_ID_APPOINTMENT_DELETE, tabIdAppointmentToDelete );
                return getConfirmRemoveMassAppointment( request, nIdForm );
            }
        }

        UrlItem url = new UrlItem( JSP_MANAGE_APPOINTMENTS );
        url.addParameter( MVCUtils.PARAMETER_VIEW, VIEW_MANAGE_APPOINTMENTS );
        url.addParameter( PARAMETER_ID_FORM, strIdForm );
        String strUrl = url.getUrl( );
        LocalizedPaginator<Integer> paginator = new LocalizedPaginator<>(_listAppointmentsIds, _nItemsPerPage, strUrl, PARAMETER_PAGE_INDEX,
                _strCurrentPageIndex, getLocale( ) );
        AppointmentFormDTO form = FormService.buildAppointmentFormLight( nIdForm );
        Map<String, Object> model = getModel( );
        model.put( MARK_FORM, form );
        model.put( MARK_FORM_MESSAGES, FormMessageService.findFormMessageByIdForm( nIdForm ) );
        model.put( MARK_NB_ITEMS_PER_PAGE, Integer.toString( _nItemsPerPage ) );
        model.put( MARK_PAGINATOR, paginator );
        model.put( MARK_LANGUAGE, getLocale( ) );
        model.put( MARK_ACTIVATE_WORKFLOW, ACTIVATEWORKFLOW );
        if ( ( form.getIdWorkflow( ) > 0 ) && WorkflowService.getInstance( ).isAvailable( ) )
        {
            StateService stateService = SpringContextService.getBean( StateService.BEAN_SERVICE );
            int nIdWorkflow = form.getIdWorkflow( );
            StateFilter stateFilter = new StateFilter( );
            stateFilter.setIdWorkflow( nIdWorkflow );
            for ( AppointmentDTO appointment : listAppointmentsDTO )
            {
                State stateAppointment = stateService.findByResource( appointment.getIdAppointment( ), Appointment.APPOINTMENT_RESOURCE_TYPE, nIdWorkflow );
                if ( stateAppointment != null )
                {
                    appointment.setState( stateAppointment );
                }
                appointment.setListWorkflowActions( WorkflowService.getInstance( ).getActions( appointment.getIdAppointment( ),
                        Appointment.APPOINTMENT_RESOURCE_TYPE, form.getIdWorkflow( ), (User) getUser( ) ) );
            }
        }
        User user = getUser( );
        model.put( MARK_APPOINTMENT_LIST, listAppointmentsDTO );
        model.put( MARK_FILTER, _filter );
        model.put( MARK_LIST_STATUS, getListStatus( ) );
        model.put( MARK_RIGHT_CREATE,
                RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, strIdForm, AppointmentResourceIdService.PERMISSION_CREATE_APPOINTMENT, user ) );
        model.put( MARK_RIGHT_DELETE,
                RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, strIdForm, AppointmentResourceIdService.PERMISSION_DELETE_APPOINTMENT, user ) );
        model.put( MARK_RIGHT_VIEW,
                RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, strIdForm, AppointmentResourceIdService.PERMISSION_VIEW_APPOINTMENT, user ) );
        model.put( MARK_RIGHT_CHANGE_STATUS, RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, strIdForm,
                AppointmentResourceIdService.PERMISSION_CHANGE_APPOINTMENT_STATUS, user ) );
        model.put( MARK_DEFAULT_FIELD_LIST, AppointmentExportService.getDefaultColumnList( getLocale( ) ) );
        model.put( MARK_CUSTOM_FIELD_LIST, AppointmentExportService.getCustomColumnList( strIdForm ) );
        model.put( MARK_LOCALE, getLocale( ) );
        model.put( MARK_APPOINTMENT_DESK_ENABLED, ( _moduleAppointmentDesk != null && _moduleAppointmentDesk.isInstalled( ) ) );

        return getPage( PROPERTY_PAGE_TITLE_MANAGE_APPOINTMENTS, TEMPLATE_MANAGE_APPOINTMENTS, model );
    }

    /**
     * Manages the removal form of a appointment whose identifier is in the HTTP request
     *
     * @param request
     *            The HTTP request
     * @return the HTML code to confirm
     */
    @Action( ACTION_CONFIRM_REMOVE_APPOINTMENT )
    public synchronized String getConfirmRemoveAppointment( HttpServletRequest request )
    {
        UrlItem url = new UrlItem( getActionUrl( ACTION_REMOVE_APPOINTMENT ) );
        url.addParameter( PARAMETER_ID_APPOINTMENT, request.getParameter( PARAMETER_ID_APPOINTMENT ) );
        url.addParameter( PARAMETER_ID_FORM, request.getParameter( PARAMETER_ID_FORM ) );
        String strMessageUrl = AdminMessageService.getMessageUrl( request, MESSAGE_CONFIRM_REMOVE_APPOINTMENT, url.getUrl( ), AdminMessage.TYPE_CONFIRMATION );
        return redirect( request, strMessageUrl );
    }

    /**
     * Handles the removal form of a appointment
     *
     * @param request
     *            The HTTP request
     * @return the JSP URL to display the form to manage appointments
     * @throws AccessDeniedException
     *             If the user is not authorized to access this feature
     */

    @Action( ACTION_REMOVE_APPOINTMENT )
    public synchronized String doRemoveAppointment( HttpServletRequest request ) throws AccessDeniedException
    {
        int nIdAppointment = Integer.parseInt( request.getParameter( PARAMETER_ID_APPOINTMENT ) );
        Integer idForm = Integer.parseInt( request.getParameter( PARAMETER_ID_FORM ) );
        if ( !RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, Integer.toString( idForm ),
                AppointmentResourceIdService.PERMISSION_DELETE_APPOINTMENT, (User) getUser( ) ) )
        {
            throw new AccessDeniedException( AppointmentResourceIdService.PERMISSION_DELETE_APPOINTMENT );
        }
        AppointmentService.deleteAppointment( nIdAppointment );
        AppLogService.info( LogUtilities.buildLog( ACTION_REMOVE_APPOINTMENT, Integer.toString( nIdAppointment ), getUser( ) ) );
        addInfo( INFO_APPOINTMENT_REMOVED, getLocale( ) );

        return redirect( request, VIEW_MANAGE_APPOINTMENTS, PARAMETER_ID_FORM, idForm );
    }

    /**
     * Manages the removal form of a appointment whose identifier is in the HTTP request
     *
     * @param request
     *            The HTTP request
     * @return the HTML code to confirm
     */
    @Action( ACTION_CONFIRM_REMOVE_MASS_APPOINTMENT )
    public synchronized String getConfirmRemoveMassAppointment( HttpServletRequest request, int nIdForm )
    {
        UrlItem url = new UrlItem( getActionUrl( ACTION_REMOVE_MASSAPPOINTMENT ) );
        url.addParameter( PARAMETER_ID_FORM, nIdForm );
        String strMessageUrl = AdminMessageService.getMessageUrl( request, MESSAGE_CONFIRM_REMOVE_MASSAPPOINTMENT, url.getUrl( ),
                AdminMessage.TYPE_CONFIRMATION );
        return redirect( request, strMessageUrl );
    }

    /**
     * Handles the removal form of a appointment
     *
     * @param request
     *            The HTTP request
     * @return the JSP URL to display the form to manage appointments
     * @throws AccessDeniedException
     *             If the user is not authorized to access this feature
     */
    @Action( ACTION_REMOVE_MASSAPPOINTMENT )
    public synchronized String doRemoveMassAppointment( HttpServletRequest request ) throws AccessDeniedException
    {
        String [ ] tabIdAppointmentToDelete = (String [ ]) request.getSession( ).getAttribute( PARAMETER_ID_APPOINTMENT_DELETE );
        request.getSession( ).removeAttribute( PARAMETER_ID_APPOINTMENT_DELETE );
        Integer idForm = Integer.parseInt( request.getParameter( PARAMETER_ID_FORM ) );
        if ( !RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, Integer.toString( idForm ),
                AppointmentResourceIdService.PERMISSION_DELETE_APPOINTMENT, (User) getUser( ) ) )
        {
            throw new AccessDeniedException( AppointmentResourceIdService.PERMISSION_DELETE_APPOINTMENT );
        }
        if ( tabIdAppointmentToDelete != null )
        {
            for ( String strIdAppointment : tabIdAppointmentToDelete )
            {
                AppointmentService.deleteAppointment( Integer.valueOf( strIdAppointment ) );
                AppLogService.info( LogUtilities.buildLog( ACTION_REMOVE_APPOINTMENT, strIdAppointment, getUser( ) ) );
            }
            addInfo( INFO_APPOINTMENT_MASSREMOVED, getLocale( ) );
        }

        return redirect( request, VIEW_MANAGE_APPOINTMENTS, PARAMETER_ID_FORM, idForm );
    }

    /**
     * View details of an appointment
     *
     * @param request
     *            The request
     * @return The HTML content to display
     * @throws AccessDeniedException
     *             If the user is not authorized to access this feature
     * @throws FileServiceException
     *             If there is an error with the file service
     */
    @View( VIEW_VIEW_APPOINTMENT )
    public synchronized String getViewAppointment( HttpServletRequest request ) throws AccessDeniedException
    {
        String strIdAppointment = request.getParameter( PARAMETER_ID_APPOINTMENT );
        String strIdForm = request.getParameter( PARAMETER_ID_FORM );
        int nIdForm = Integer.parseInt( strIdForm );
        Form form = FormService.findFormLightByPrimaryKey( nIdForm );
        int nItemsPerPage = AbstractPaginator.getItemsPerPage( request, AbstractPaginator.PARAMETER_ITEMS_PER_PAGE, _nItemsPerPage, _nDefaultItemsPerPage );
        int nIdAppointment = Integer.parseInt( strIdAppointment );
        AppointmentDTO appointmentDTO = AppointmentService.buildAppointmentDTOFromIdAppointment( nIdAppointment );
        if ( !RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, strIdForm, AppointmentResourceIdService.PERMISSION_VIEW_APPOINTMENT,
                (User) getUser( ) ) )
        {
            throw new AccessDeniedException( AppointmentResourceIdService.PERMISSION_VIEW_APPOINTMENT );
        }
        Map<String, Object> model = getModel( );
        model.put( MARK_APPOINTMENT, appointmentDTO );
        if ( appointmentDTO.getAdminUserCreate( ) != null )
        {

            model.put( MARK_USER_CREATOR, AdminUserHome.findUserByLogin( appointmentDTO.getAdminUserCreate( ) ) );
        }
        model.put( MARK_FORM_MESSAGES, FormMessageService.findFormMessageByIdForm( nIdForm ) );
        model.put( MARK_FORM, form );
        model.put( MARK_NB_ITEMS_PER_PAGE, Integer.toString( nItemsPerPage ) );
        if ( ( form.getIdWorkflow( ) > 0 ) && WorkflowService.getInstance( ).isAvailable( ) )
        {
            model.put( MARK_RESOURCE_HISTORY, WorkflowService.getInstance( ).getDisplayDocumentHistory( nIdAppointment, Appointment.APPOINTMENT_RESOURCE_TYPE,
                    form.getIdWorkflow( ), request, getLocale( ), (User) getUser( ) ) );
        }
        if ( ( form.getIdWorkflow( ) > 0 ) && WorkflowService.getInstance( ).isAvailable( ) )
        {
            StateService stateService = SpringContextService.getBean( StateService.BEAN_SERVICE );
            int nIdWorkflow = form.getIdWorkflow( );
            StateFilter stateFilter = new StateFilter( );
            stateFilter.setIdWorkflow( nIdWorkflow );
            State stateAppointment = stateService.findByResource( appointmentDTO.getIdAppointment( ), Appointment.APPOINTMENT_RESOURCE_TYPE, nIdWorkflow );
            if ( stateAppointment != null )
            {
                appointmentDTO.setState( stateAppointment );
            }
            appointmentDTO.setListWorkflowActions( WorkflowService.getInstance( ).getActions( appointmentDTO.getIdAppointment( ),
                    Appointment.APPOINTMENT_RESOURCE_TYPE, form.getIdWorkflow( ), (User) getUser( ) ) );
        }
        Locale locale = getLocale( );
        List<Response> listResponse = AppointmentResponseService.findListResponse( nIdAppointment );
        for ( Response response : listResponse )
        {
            if ( response.getFile( ) != null )
            {
            	// load from default generic attribute file service
            	File file = GenericAttributeFileService.getInstance().load( response.getFile( ).getFileKey( ), null);


                response.setFile( file );
            }
            if ( response.getEntry( ) != null )
            {
                response.setEntry( EntryHome.findByPrimaryKey( response.getEntry( ).getIdEntry( ) ) );
            }
        }
        appointmentDTO.setListResponse( listResponse );
        model.put( MARK_LIST_RESPONSE_RECAP_DTO, AppointmentUtilities.buildListResponse( appointmentDTO, request, locale ) );
        model.put( MARK_ADDON, AppointmentAddOnManager.getAppointmentAddOn( appointmentDTO.getIdAppointment( ), getLocale( ) ) );
        User user = getUser( );
        model.put( MARK_RIGHT_CREATE,
                RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, strIdForm, AppointmentResourceIdService.PERMISSION_CREATE_APPOINTMENT, user ) );
        model.put( MARK_RIGHT_DELETE,
                RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, strIdForm, AppointmentResourceIdService.PERMISSION_DELETE_APPOINTMENT, user ) );
        model.put( MARK_RIGHT_VIEW,
                RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, strIdForm, AppointmentResourceIdService.PERMISSION_VIEW_APPOINTMENT, user ) );
        model.put( MARK_RIGHT_CHANGE_STATUS, RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, strIdForm,
                AppointmentResourceIdService.PERMISSION_CHANGE_APPOINTMENT_STATUS, user ) );
        model.put( MARK_LANGUAGE, getLocale( ) );
        model.put( MARK_ACTIVATE_WORKFLOW, ACTIVATEWORKFLOW );
        model.put( MARK_LOCALE, getLocale( ) );
        return getPage( PROPERTY_PAGE_TITLE_VIEW_APPOINTMENT, TEMPLATE_VIEW_APPOINTMENT, model );
    }

    /**
     * Do download a file from an appointment response
     *
     * @param request
     *            The request
     * @param response
     *            The response
     * @return nothing.
     * @throws AccessDeniedException
     *             If the user is not authorized to access this feature
     */
    @Action( ACTION_EXPORT_APPOINTMENTS )
    public synchronized String doExportAppointments( HttpServletRequest request ) throws AccessDeniedException
    {
        String strIdForm = request.getParameter( PARAMETER_ID_FORM );
        if ( StringUtils.isEmpty( strIdForm ) || !StringUtils.isNumeric( strIdForm ) )
        {
            return redirect( request, AppointmentFormJspBean.getURLManageAppointmentForms( request ) );
        }
        if ( !RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, strIdForm, AppointmentResourceIdService.PERMISSION_VIEW_APPOINTMENT,
                (User) getUser( ) ) )
        {
            throw new AccessDeniedException( AppointmentResourceIdService.PERMISSION_VIEW_APPOINTMENT );
        }
        Locale locale = getLocale( );
        List<AppointmentDTO> listAppointmentsDTO = new ArrayList<>( );
        if ( _filter != null && _filter.getIdForm( ) == Integer.parseInt( strIdForm ) )
        {

            listAppointmentsDTO = AppointmentService.findListAppointmentsDTOByFilter( _filter );
        }

        List<String> defaultColumnList = new ArrayList<>( );
        List<Integer> customColumnList = new ArrayList<>( );
        if ( ArrayUtils.isNotEmpty( request.getParameterValues( PARAMETER_SELECTED_DEFAULT_FIELD ) ) )
        {
            defaultColumnList = Arrays.asList( request.getParameterValues( PARAMETER_SELECTED_DEFAULT_FIELD ) );
        }
        if ( ArrayUtils.isNotEmpty( request.getParameterValues( PARAMETER_SELECTED_CUSTOM_FIELD ) ) )
        {
            customColumnList = Arrays.asList( request.getParameterValues( PARAMETER_SELECTED_CUSTOM_FIELD ) ).stream( ).map( Integer::parseInt )
                    .collect( Collectors.toList( ) );
        }

        ExcelAppointmentGenerator generator = new ExcelAppointmentGenerator( defaultColumnList, locale, listAppointmentsDTO, customColumnList );

        TemporaryFileGeneratorService.getInstance( ).generateFile( generator, getUser( ) );
        addInfo( "appointment.export.async.message", getLocale( ) );

        return getManageAppointments( request );
    }

    /**
     * Returns the form to create an appointment
     *
     * @param request
     *            The HTTP request
     * @return the HTML code of the appointment form
     * @throws AccessDeniedException
     *             If the user is not authorized to access this feature
     */
    @View( VIEW_CREATE_APPOINTMENT )
    public synchronized String getViewCreateAppointment( HttpServletRequest request ) throws AccessDeniedException
    {
        clearUploadFilesIfNeeded( request.getSession( ) );
        String strIdForm = request.getParameter( PARAMETER_ID_FORM );
        int nIdForm = Integer.parseInt( strIdForm );

        String strNbPlacesToTake = request.getParameter( PARAMETER_NB_PLACE_TO_TAKE );
        if ( !RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, strIdForm, AppointmentResourceIdService.PERMISSION_CREATE_APPOINTMENT,
                (User) getUser( ) ) )
        {
            throw new AccessDeniedException( AppointmentResourceIdService.PERMISSION_CREATE_APPOINTMENT );
        }
        FormRule formRule = FormRuleService.findFormRuleWithFormId( nIdForm );
        Locale locale = getLocale( );

        String isModification = request.getParameter( PARAMETER_IS_MODIFICATION );
        boolean bModificationForm = false;
        List<Slot> listSlot = null;
        if ( isModification != null )

        {
            bModificationForm = true;

        }
        else
        {
            if ( strNbPlacesToTake != null )
            {

                _nNbPlacesToTake = Integer.parseInt( strNbPlacesToTake );
            }
            int nNbConsecutiveSlot = ( _nNbPlacesToTake == 0 ) ? 1 : _nNbPlacesToTake;
            LocalDateTime startingDateTime = LocalDateTime.parse( request.getParameter( PARAMETER_STARTING_DATE_TIME ) );

            // Get all the week definitions
            List<WeekDefinition> listWeekDefinition = WeekDefinitionService.findListWeekDefinition( nIdForm );
            Map<WeekDefinition, ReservationRule> mapReservationRule = ReservationRuleService.findAllReservationRule( nIdForm, listWeekDefinition );

            listSlot = SlotService.buildListSlot( nIdForm, mapReservationRule, startingDateTime.toLocalDate( ), startingDateTime.toLocalDate( ) );

            if ( formRule.getBoOverbooking( ) && RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, strIdForm,
                    AppointmentResourceIdService.PERMISSION_OVERBOOKING_FORM, (User) getUser( ) ) )
            {
                listSlot = listSlot.stream( ).filter( s -> ( ( startingDateTime.compareTo( s.getStartingDateTime( ) ) <= 0 ) && ( s.getIsOpen( ) ) ) )
                        .limit( nNbConsecutiveSlot ).collect( Collectors.toList( ) );

            }
            else
            {
                listSlot = listSlot.stream( ).filter(
                        s -> ( ( startingDateTime.compareTo( s.getStartingDateTime( ) ) <= 0 ) && ( s.getNbRemainingPlaces( ) > 0 ) && ( s.getIsOpen( ) ) ) )
                        .limit( nNbConsecutiveSlot ).collect( Collectors.toList( ) );

            }
            if ( listSlot == null || listSlot.stream( ).noneMatch( slot -> slot.getStartingDateTime( ).isEqual( startingDateTime ) )
                    || ( _nNbPlacesToTake > 0 && _nNbPlacesToTake != listSlot.size( ) ) || !AppointmentUtilities.isConsecutiveSlots( listSlot ) )
            {
                addError( ERROR_MESSAGE_SLOT_FULL, locale );
                return redirect( request, VIEW_CALENDAR_MANAGE_APPOINTMENTS, PARAMETER_ID_FORM, nIdForm );
            }
        }

        // Get the not validated appointment in session if it exists

        if ( _notValidatedAppointment == null )
        {

            if ( _validatedAppointment != null )
            {

                // Try to get the validated appointment in session
                // (in case the user click on back button in the recap view (or
                // modification)
                _notValidatedAppointment = _validatedAppointment;
                _validatedAppointment = null;
            }
            else
            {
                // Need to get back the informations the user has entered
                _notValidatedAppointment = new AppointmentDTO( );

            }
        }
        else
            if ( _nNbPlacesToTake == 0 && bModificationForm )
            {
                _nNbPlacesToTake = _notValidatedAppointment.getNbBookedSeats( );
            }
        if ( !bModificationForm )
        {
            boolean bool = true;
            _notValidatedAppointment.setSlot( null );
            _notValidatedAppointment.setIdForm( nIdForm );
            _notValidatedAppointment.setNbMaxPotentialBookedSeats( 0 );
            for ( Slot slot : listSlot )
            {

                if ( slot.getIdSlot( ) == 0 )
                {

                    slot = SlotSafeService.createSlot( slot );

                }
                else
                {

                    slot = SlotService.findSlotById( slot.getIdSlot( ) );
                }

                // Need to check competitive access
                // May be the slot is already taken at the same time
                if ( slot.getNbPotentialRemainingPlaces( ) <= 0 && !formRule.getBoOverbooking( ) && !RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE,
                        strIdForm, AppointmentResourceIdService.PERMISSION_OVERBOOKING_FORM, (User) getUser( ) ) )
                {
                    _notValidatedAppointment = null;
                    addError( ERROR_MESSAGE_SLOT_FULL, locale );
                    return redirect( request, VIEW_CALENDAR_MANAGE_APPOINTMENTS, PARAMETER_ID_FORM, nIdForm );
                }

                _notValidatedAppointment.addSlot( slot );

                if ( bool )
                {

                    LocalDateTime startingDateTime = LocalDateTime.parse( request.getParameter( PARAMETER_STARTING_DATE_TIME ) );

                    _notValidatedAppointment.setDateOfTheAppointment( slot.getDate( ).format( Utilities.getFormatter( ) ) );
                    _notValidatedAppointment.setEndingDateTime( listSlot.get( listSlot.size( ) - 1 ).getEndingDateTime( ) );
                    _notValidatedAppointment.setStartingDateTime( startingDateTime );
                    ReservationRule reservationRule = ReservationRuleService.findReservationRuleByIdFormAndClosestToDateOfApply( nIdForm, slot.getDate( ) );
                    _appointmentForm = FormService.buildAppointmentForm( nIdForm, reservationRule );
                    bool = false;
                }
                AppointmentUtilities.putTimerInSession( request, slot.getIdSlot( ), _notValidatedAppointment, _appointmentForm.getMaxPeoplePerAppointment( ) );
            }

            if ( _notValidatedAppointment.getNbMaxPotentialBookedSeats( ) == 0 && !formRule.getBoOverbooking( ) && !RBACService
                    .isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, strIdForm, AppointmentResourceIdService.PERMISSION_OVERBOOKING_FORM, (User) getUser( ) ) )

            {
                addError( ERROR_MESSAGE_SLOT_FULL, locale );
                return redirect( request, VIEW_CALENDAR_MANAGE_APPOINTMENTS, PARAMETER_ID_FORM, nIdForm );
            }
        }

        Map<String, Object> model = getModel( );
        if ( CollectionUtils.isNotEmpty( listFormErrors ) )
        {
            model.put( MARK_FORM_ERRORS, listFormErrors );
            listFormErrors = new ArrayList<>( );
        }
        List<Entry> listEntryFirstLevel = EntryService.getFilter( _appointmentForm.getIdForm( ), false );
        StringBuilder strBuffer = new StringBuilder( );
        for ( Entry entry : listEntryFirstLevel )
        {
            EntryService.getHtmlEntry( model, entry.getIdEntry( ), strBuffer, locale, false, _notValidatedAppointment );
        }

        boolean isOverbooking = !_appointmentForm.getIsMultislotAppointment( ) && formRule.getBoOverbooking( ) && RBACService
                .isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, strIdForm, AppointmentResourceIdService.PERMISSION_OVERBOOKING_FORM, (User) getUser( ) );

        model.put( MARK_STR_ENTRY, strBuffer.toString( ) );
        model.put( MARK_FORM, _appointmentForm );
        model.put( MARK_APPOINTMENT, _notValidatedAppointment );
        model.put( PARAMETER_DATE_OF_DISPLAY, _notValidatedAppointment.getSlot( ).get( 0 ).getDate( ) );
        model.put( MARK_PLACES, _notValidatedAppointment.getNbMaxPotentialBookedSeats( ) );
        model.put( MARK_IS_OVERBOOKING, isOverbooking );
        FormMessage formMessages = FormMessageService.findFormMessageByIdForm( nIdForm );
        model.put( MARK_FORM_MESSAGES, formMessages );
        model.put( MARK_LOCALE, locale );
        model.put( MARK_LIST_ERRORS, AppointmentDTO.getAllErrors( locale ) );
        HtmlTemplate templateForm = AppTemplateService.getTemplate( TEMPLATE_HTML_CODE_FORM_ADMIN, getLocale( ), model );
        model.put( MARK_FORM_HTML, templateForm.getHtml( ) );
        model.put( MARK_LOCALE, getLocale( ) );


        return getPage( PROPERTY_PAGE_TITLE_CREATE_APPOINTMENT, TEMPLATE_CREATE_APPOINTMENT, model );
    }

    /**
     * Do validate data entered by a user to fill a form
     *
     * @param request
     *            The request
     * @return The next URL to redirect to
     * @throws AccessDeniedException
     *             If the user is not authorized to access this feature
     */
    @Action( ACTION_DO_VALIDATE_FORM )
    public synchronized String doValidateForm( HttpServletRequest request ) throws AccessDeniedException
    {
        String strIdForm = request.getParameter( PARAMETER_ID_FORM );
        int nIdForm = Integer.parseInt( strIdForm );
        String strEmail = request.getParameter( PARAMETER_EMAIL );
        String strEmailConfirm = request.getParameter( PARAMETER_EMAIL_CONFIRMATION );
        Locale locale = getLocale( );
        AppointmentUtilities.checkDateOfTheAppointmentIsNotBeforeNow( _notValidatedAppointment, locale, listFormErrors );
        AppointmentUtilities.checkEmail( strEmail, strEmailConfirm, _appointmentForm, locale, listFormErrors );

        if ( _appointmentForm.getBoOverbooking( ) && RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, strIdForm,
                AppointmentResourceIdService.PERMISSION_OVERBOOKING_FORM, (User) getUser( ) ) )
        {

            _notValidatedAppointment.setOverbookingAllowed( true );
        }

        int nbBookedSeats = _nNbPlacesToTake;
        if ( _nNbPlacesToTake == 0 )
        {

            nbBookedSeats = AppointmentUtilities.checkAndReturnNbBookedSeats( request.getParameter( PARAMETER_NUMBER_OF_BOOKED_SEATS ), _appointmentForm,
                    _notValidatedAppointment, locale, listFormErrors );

        }

        if ( _appointmentForm.getEnableMandatoryEmail( )
                && !AppointmentUtilities.checkNbDaysBetweenTwoAppointmentsTaken( _notValidatedAppointment, strEmail, _appointmentForm ) )
        {
            addWarning( ERROR_MESSAGE_NB_MIN_DAYS_BETWEEN_TWO_APPOINTMENTS, locale );
        }
        if ( _appointmentForm.getEnableMandatoryEmail( )
                && !AppointmentUtilities.checkNbMaxAppointmentsOnAGivenPeriod( _notValidatedAppointment, strEmail, _appointmentForm ) )
        {
            addWarning( ERROR_MESSAGE_NB_MAX_APPOINTMENTS_ON_A_PERIOD, locale );
        }
        List<AppointmentDTO> listAppointments = new ArrayList<>( );
        if ( _appointmentForm.getEnableMandatoryEmail( )
                && !AppointmentUtilities.checkNbMaxAppointmentsDefinedOnCategory( _notValidatedAppointment, strEmail, _appointmentForm, listAppointments ) )
        {
            StringJoiner builder = new StringJoiner( StringUtils.SPACE );
            String lf = System.getProperty( "line.separator" );
            for ( AppointmentDTO appt : listAppointments )
            {
                builder.add( appt.getLastName( ) );
                builder.add( appt.getFirstName( ) );
                builder.add( appt.getDateOfTheAppointment( ) );
                builder.add( appt.getStartingTime( ).toString( ) );
                builder.add( lf );
            }
            Object [ ] tabAppointment = {
                    builder.toString( )
            };

            String strErrorMessageDateWithAppointments = I18nService.getLocalizedString( ERROR_MESSAGE_NB_MAX_APPOINTMENTS_ON_A_CATEGORY, tabAppointment,
                    locale );
            addWarning( strErrorMessageDateWithAppointments );
        }
        AppointmentUtilities.fillAppointmentDTO( _notValidatedAppointment, nbBookedSeats, strEmail, strEmailConfirm, request.getParameter( PARAMETER_FIRST_NAME ),
                request.getParameter( PARAMETER_LAST_NAME ) );
        AppointmentUtilities.validateFormAndEntries( _notValidatedAppointment, request, listFormErrors, true );
        AppointmentUtilities.fillInListResponseWithMapResponse( _notValidatedAppointment );
        AppointmentUtilities.setAppointmentPhoneNumberValuesFromResponse( _notValidatedAppointment );

        if ( CollectionUtils.isNotEmpty( listFormErrors ) )
        {
            getModel( ).put( MARK_FORM_ERRORS, listFormErrors );
            for ( GenericAttributeError error : listFormErrors )
            {
                addError( error.getErrorMessage( ) );
            }
            LinkedHashMap<String, String> additionalParameters = new LinkedHashMap<>( );
            additionalParameters.put( PARAMETER_ID_FORM, strIdForm );
            additionalParameters.put( PARAMETER_STARTING_DATE_TIME, _notValidatedAppointment.getStartingDateTime( ).toString( ) );
            additionalParameters.put( PARAMETER_ENDING_DATE_TIME, _notValidatedAppointment.getEndingDateTime( ).toString( ) );
            return redirect( request, VIEW_CREATE_APPOINTMENT, additionalParameters );
        }
        _validatedAppointment = _notValidatedAppointment;
        _notValidatedAppointment = null;
        return redirect( request, VIEW_DISPLAY_RECAP_APPOINTMENT, PARAMETER_ID_FORM, nIdForm );
    }

    /**
     * Return to the display recap view with the new date selected on the calendar
     *
     * @param request
     *            the request
     * @return to the display recap view
     * @throws FileServiceException
     *             If there is an error with the file service
     */
    @View( VIEW_CHANGE_DATE_APPOINTMENT )
    public synchronized String getViewChangeDateAppointment( HttpServletRequest request )
    {
        String strIdForm = request.getParameter( PARAMETER_ID_FORM );
        Locale locale = getLocale( );
        int nIdForm = Integer.parseInt( strIdForm );
        LocalDateTime startingDateTime = LocalDateTime.parse( request.getParameter( PARAMETER_STARTING_DATE_TIME ) );
        LocalDateTime endingDateTime = LocalDateTime.parse( request.getParameter( PARAMETER_ENDING_DATE_TIME ) );
        // Get all the week definitions
        List<WeekDefinition> listWeekDefinition = WeekDefinitionService.findListWeekDefinition( nIdForm );
        Map<WeekDefinition, ReservationRule> mapReservationRule = ReservationRuleService.findAllReservationRule( nIdForm, listWeekDefinition );

        List<Slot> listSlot = SlotService.buildListSlot( nIdForm, mapReservationRule, startingDateTime.toLocalDate( ), endingDateTime.toLocalDate( ) );

        FormRule formRule = FormRuleService.findFormRuleWithFormId( nIdForm );
        if ( formRule.getBoOverbooking( ) && RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, strIdForm,
                AppointmentResourceIdService.PERMISSION_OVERBOOKING_FORM, (User) getUser( ) ) )
        {

            listSlot = listSlot.stream( ).filter( s -> ( ( startingDateTime.compareTo( s.getStartingDateTime( ) ) <= 0 )
                    && ( endingDateTime.compareTo( s.getEndingDateTime( ) ) >= 0 ) && ( s.getIsOpen( ) ) ) ).collect( Collectors.toList( ) );

        }
        else
        {

            listSlot = listSlot.stream( )
                    .filter( s -> ( ( startingDateTime.compareTo( s.getStartingDateTime( ) ) <= 0 )
                            && ( endingDateTime.compareTo( s.getEndingDateTime( ) ) >= 0 ) && ( s.getNbRemainingPlaces( ) > 0 ) && ( s.getIsOpen( ) ) ) )
                    .collect( Collectors.toList( ) );
        }
        boolean bool = true;

        // If nIdSlot == 0, the slot has not been created yet
        _validatedAppointment.setSlot( null );
        _validatedAppointment.setNbMaxPotentialBookedSeats( 0 );
        for ( Slot slot : listSlot )
        {
            if ( slot.getIdSlot( ) == 0 )
            {
                slot = SlotSafeService.createSlot( slot );
            }
            else
            {
                slot = SlotService.findSlotById( slot.getIdSlot( ) );
            }

            if ( bool )
            {
                _validatedAppointment.setDateOfTheAppointment( slot.getDate( ).format( Utilities.getFormatter( ) ) );
                ReservationRule reservationRule = ReservationRuleService.findReservationRuleByIdFormAndClosestToDateOfApply( nIdForm, slot.getDate( ) );
                _appointmentForm = FormService.buildAppointmentForm( nIdForm, reservationRule );
                bool = false;
            }
            // Need to check competitive access
            // May be the slot is already taken at the same time
            if ( slot.getNbPotentialRemainingPlaces( ) <= 0
                    && ( !_appointmentForm.getBoOverbooking( ) || !RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, strIdForm,
                            AppointmentResourceIdService.PERMISSION_OVERBOOKING_FORM, (User) getUser( ) ) ) )
            {
                addError( ERROR_MESSAGE_SLOT_FULL, locale );
                return redirect( request, VIEW_CALENDAR_MANAGE_APPOINTMENTS, PARAMETER_ID_FORM, nIdForm );
            }

            _validatedAppointment.addSlot( slot );

            AppointmentUtilities.putTimerInSession( request, slot.getIdSlot( ), _validatedAppointment, _appointmentForm.getMaxPeoplePerAppointment( ) );
        }

        if ( _validatedAppointment.getNbMaxPotentialBookedSeats( ) == 0 && ( !_appointmentForm.getBoOverbooking( ) || !RBACService
                .isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, strIdForm, AppointmentResourceIdService.PERMISSION_OVERBOOKING_FORM, (User) getUser( ) ) ) )
        {
            addError( ERROR_MESSAGE_SLOT_FULL, locale );
            return redirect( request, VIEW_CALENDAR_MANAGE_APPOINTMENTS, PARAMETER_ID_FORM, nIdForm );
        }

        for ( Response response : _validatedAppointment.getListResponse( ) )
        {
            if ( response.getFile( ) != null )
            {
            	// load from default generic attribute file service
            	File file = GenericAttributeFileService.getInstance().load( response.getFile( ).getFileKey( ), null);

                response.setFile( file );
            }
        }

        Map<String, String> additionalParameters = new HashMap<>( );
        additionalParameters.put( PARAMETER_ID_FORM, Integer.toString( nIdForm ) );
        additionalParameters.put( PARAMETER_COME_FROM_CALENDAR, Boolean.TRUE.toString( ) );
        return redirect( request, VIEW_DISPLAY_RECAP_APPOINTMENT, additionalParameters );
    }

    /**
     * Display the recap before validating an appointment
     *
     * @param request
     *            The request
     * @return The HTML content to display or the next URL to redirect to
     */
    @View( VIEW_DISPLAY_RECAP_APPOINTMENT )
    public synchronized String displayRecapAppointment( HttpServletRequest request )
    {

        Map<String, Object> model = getModel( );
        String strComeFromCalendar = request.getParameter( PARAMETER_COME_FROM_CALENDAR );
        if ( StringUtils.isNotEmpty( strComeFromCalendar ) )
        {
            model.put( PARAMETER_COME_FROM_CALENDAR, strComeFromCalendar );
            model.put( PARAMETER_DATE_OF_DISPLAY, _validatedAppointment.getSlot( ).get( 0 ).getDate( ) );
        }
        model.put( MARK_FORM_MESSAGES, FormMessageService.findFormMessageByIdForm( _validatedAppointment.getIdForm( ) ) );
        model.put( MARK_APPOINTMENT, _validatedAppointment );
        Locale locale = getLocale( );
        model.put( MARK_ADDON, AppointmentAddOnManager.getAppointmentAddOn( _validatedAppointment.getIdAppointment( ), getLocale( ) ) );
        model.put( MARK_LIST_RESPONSE_RECAP_DTO, AppointmentUtilities.buildListResponse( _validatedAppointment, request, locale ) );
        model.put( MARK_FORM, _appointmentForm );
        model.put( MARK_LOCALE, getLocale( ) );
        return getPage( PROPERTY_PAGE_TITLE_RECAP_APPOINTMENT, TEMPLATE_APPOINTMENT_FORM_RECAP, model );
    }

    /**
     * Do save an appointment into the database if it is valid
     *
     * @param request
     *            The request
     * @return The XPage to display
     * @throws AccessDeniedException
     *             If the user is not authorized to access this feature
     */
    @Action( ACTION_DO_MAKE_APPOINTMENT )
    public synchronized String doMakeAppointment( HttpServletRequest request ) throws AccessDeniedException
    {
        boolean overbookingAllowed = false;
        if ( StringUtils.isNotEmpty( request.getParameter( PARAMETER_BACK ) ) )
        {
            return redirect( request, VIEW_CREATE_APPOINTMENT, PARAMETER_ID_FORM, _validatedAppointment.getIdForm( ) );
        }
        if ( _appointmentForm.getBoOverbooking( ) && RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE,
                Integer.toString( _appointmentForm.getIdForm( ) ), AppointmentResourceIdService.PERMISSION_OVERBOOKING_FORM, (User) getUser( ) ) )
        {
            overbookingAllowed = true;
        }
        if ( !RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, Integer.toString( _appointmentForm.getIdForm( ) ),
                AppointmentResourceIdService.PERMISSION_CREATE_APPOINTMENT, (User) getUser( ) ) || !_appointmentForm.getIsActive( ) )
        {
            throw new AccessDeniedException( AppointmentResourceIdService.PERMISSION_CREATE_APPOINTMENT );
        }
        int nIdAppointment;
        if ( _validatedAppointment.getIdAppointment( ) == 0 )
        {
            // set the admin user who is creating the appointment
            AdminUser adminLuteceUser = AdminAuthenticationService.getInstance( ).getRegisteredUser( request );
            _validatedAppointment.setAdminUserCreate( adminLuteceUser.getAccessCode( ) );
        }
        try
        {
            _validatedAppointment.setOverbookingAllowed( overbookingAllowed );
            nIdAppointment = SlotSafeService.saveAppointment( _validatedAppointment, request );

        }
        catch( SlotFullException e )
        {
            addError( ERROR_MESSAGE_SLOT_FULL, getLocale( ) );
            return redirect( request, VIEW_CALENDAR_MANAGE_APPOINTMENTS, PARAMETER_ID_FORM, _validatedAppointment.getIdForm( ) );
        }
        catch( SlotEditTaskExpiredTimeException e )
        {
            addError( ERROR_MESSAGE_SLOT_EDIT_TASK_EXPIRED_TIME, getLocale( ) );
            return redirect( request, VIEW_CALENDAR_MANAGE_APPOINTMENTS, PARAMETER_ID_FORM, _validatedAppointment.getIdForm( ) );
        }
        catch( AppointmentSavedException e )
        {

            nIdAppointment = _validatedAppointment.getIdAppointment( );
            AppLogService.error( "Error Save appointment: " + e.getMessage( ), e );
        }
        _nNbPlacesToTake = 0;
        AppLogService.info( LogUtilities.buildLog( ACTION_DO_MAKE_APPOINTMENT, Integer.toString( nIdAppointment ), getUser( ) ) );
        addInfo( INFO_APPOINTMENT_CREATED, getLocale( ) );
        AppointmentAsynchronousUploadHandler.getHandler( ).removeSessionFiles( request.getSession( ) );
        Map<String, String> additionalParameters = new HashMap<>( );
        additionalParameters.put( PARAMETER_ID_FORM, Integer.toString( _appointmentForm.getIdForm( ) ) );
        additionalParameters.put( PARAMETER_DATE_OF_DISPLAY, _validatedAppointment.getSlot( ).get( 0 ).getDate( ).toString( ) );
        _validatedAppointment = null;
        return redirect( request, VIEW_CALENDAR_MANAGE_APPOINTMENTS, additionalParameters );
    }

    /**
     * Do download a file from an appointment response stored in session and not yet on server fs
     *
     * @param request
     *            The request
     * @param httpResponse
     *            The response
     * @return nothing.
     * @throws AccessDeniedException
     *             If the user is not authorized to access this feature
     */
    public synchronized String getDownloadFileFromSession( HttpServletRequest request, HttpServletResponse httpResponse ) throws AccessDeniedException
    {
        String strIdResponse = request.getParameter( PARAMETER_ID_RESPONSE );
        File respfile = null;
        if ( StringUtils.isEmpty( strIdResponse ) || !StringUtils.isNumeric( strIdResponse ) )
        {
            return redirect( request, AppointmentFormJspBean.getURLManageAppointmentForms( request ) );
        }

        int nIdResponse = Integer.parseInt( strIdResponse );
        List<Response> lResponse = _validatedAppointment.getListResponse( );

        for ( Response response : lResponse )
        {
            if ( response.getEntry( ).getIdEntry( ) == nIdResponse && response.getFile( ) != null )
            {
                respfile = response.getFile( );
                break;
            }
        }

        if ( respfile == null )
        {
            return redirect( request, AppointmentFormJspBean.getURLManageAppointmentForms( request ) );
        }

        httpResponse.setHeader( "Content-Disposition", "attachment; filename=\"" + respfile.getTitle( ) + "\";" );
        httpResponse.setHeader( "Content-type", respfile.getMimeType( ) );
        httpResponse.addHeader( "Content-Encoding", "UTF-8" );
        httpResponse.addHeader( "Pragma", "public" );
        httpResponse.addHeader( "Expires", "0" );
        httpResponse.addHeader( "Cache-Control", "must-revalidate,post-check=0,pre-check=0" );

        try
        {
            OutputStream os = httpResponse.getOutputStream( );
            os.write( respfile.getPhysicalFile( ).getValue( ) );
            // We do not close the output stream in finally clause because it is
            // the response stream,
            // and an error message needs to be displayed if an exception occurs
            os.close( );
        }
        catch( IOException e )
        {
            AppLogService.error( e.getStackTrace( ), e );
        }

        return StringUtils.EMPTY;
    }

    /**
     * Do download a file from an appointment response
     *
     * @param request
     *            The request
     * @param httpResponse
     *            The response
     * @return nothing.
     * @throws AccessDeniedException
     *             If the user is not authorized to access this feature
     * @throws FileServiceException
     *             If there is an error with the file service
     */
    public synchronized String getDownloadFile( HttpServletRequest request, HttpServletResponse httpResponse ) throws AccessDeniedException
    {
        String strIdResponse = request.getParameter( PARAMETER_ID_RESPONSE );

        if ( StringUtils.isEmpty( strIdResponse ) || !StringUtils.isNumeric( strIdResponse ) )
        {
            return redirect( request, AppointmentFormJspBean.getURLManageAppointmentForms( request ) );
        }

        int nIdResponse = Integer.parseInt( strIdResponse );
        Response response = ResponseHome.findByPrimaryKey( nIdResponse );

        // load from default generic attribute file service
    	File file = GenericAttributeFileService.getInstance().load( response.getFile( ).getFileKey( ), null);

        httpResponse.setHeader( "Content-Disposition", "attachment; filename=\"" + file.getTitle( ) + "\";" );
        httpResponse.setHeader( "Content-type", file.getMimeType( ) );
        httpResponse.addHeader( "Content-Encoding", "UTF-8" );
        httpResponse.addHeader( "Pragma", "public" );
        httpResponse.addHeader( "Expires", "0" );
        httpResponse.addHeader( "Cache-Control", "must-revalidate,post-check=0,pre-check=0" );

        try
        {
            OutputStream os = httpResponse.getOutputStream( );
            os.write( file.getPhysicalFile( ).getValue( ) );
            // We do not close the output stream in finnaly clause because it is
            // the response stream,
            // and an error message needs to be displayed if an exception occurs
            os.close( );
        }
        catch( IOException e )
        {
            AppLogService.error( e.getStackTrace( ), e );
        }

        return StringUtils.EMPTY;
    }

    /**
     * Default constructor
     */
    public AppointmentJspBean( )
    {
        _nDefaultItemsPerPage = AppPropertiesService.getPropertyInt( PROPERTY_DEFAULT_LIST_APPOINTMENT_PER_PAGE, 10 );
    }

    private void cleanSession( HttpSession session )
    {
        _filter = null;
        _strCurrentPageIndex = null;
        _notValidatedAppointment = null;
        _validatedAppointment = null;
        AppointmentAsynchronousUploadHandler.getHandler( ).removeSessionFiles( session );
    }

    /**
     * Clear uploaded files if needed.
     *
     * @param session
     *            The session of the current user
     */
    private void clearUploadFilesIfNeeded( HttpSession session )
    {
        // If we do not reload an appointment, we clear uploaded files.
        if ( _notValidatedAppointment == null && _validatedAppointment == null )
        {
            AppointmentAsynchronousUploadHandler.getHandler( ).removeSessionFiles( session );
        }
    }

    /**
     * Order the list of the appointment in the result tab with the order by and order asc given
     *
     * @param listAppointmentsDTO
     *            the llist of appointments
     */
    private List<AppointmentDTO> orderList( List<AppointmentDTO> listAppointmentsDTO )
    {
        List<AppointmentDTO> sortedList = new ArrayList<>( );
        if ( CollectionUtils.isNotEmpty( listAppointmentsDTO ) )
        {
            sortedList.addAll( listAppointmentsDTO );
        }

        Stream<AppointmentDTO> stream = null;
        switch( _filter.getOrderBy( ) )
        {
            case LAST_NAME:
                stream = sortedList.stream( ).sorted( ( a1, a2 ) -> a1.getLastName( ).compareTo( a2.getLastName( ) ) );
                break;
            case FIRST_NAME:
                stream = sortedList.stream( ).sorted( ( a1, a2 ) -> a1.getFirstName( ).compareTo( a2.getFirstName( ) ) );
                break;
            case EMAIL:
                stream = sortedList.stream( ).sorted( ( a1, a2 ) -> a1.getEmail( ).compareTo( a2.getEmail( ) ) );
                break;
            case PHONE_NUMBER:
            	// Added 'Comparator.nullsLast' to avoid NullPointerException when comparing null values (a user's phone number might be NULL in the database)
            	stream = sortedList.stream( ).sorted( Comparator.comparing( AppointmentDTO::getPhoneNumber, Comparator.nullsLast( Comparator.naturalOrder() ) ) );
                break;
            case NB_BOOKED_SEATS:
                stream = sortedList.stream( ).sorted( ( a1, a2 ) -> Integer.compare( a1.getNbBookedSeats( ), a2.getNbBookedSeats( ) ) );
                break;
            case DATE_APPOINTMENT:
                stream = sortedList.stream( ).sorted( ( a1, a2 ) -> a1.getStartingDateTime( ).compareTo( a2.getStartingDateTime( ) ) );
                break;
            case ADMIN:
                stream = sortedList.stream( ).sorted( ( a1, a2 ) -> a1.getAdminUser( ).compareTo( a2.getAdminUser( ) ) );
                break;
            case STATUS:
                stream = sortedList.stream( ).sorted( ( a1, a2 ) -> Boolean.compare( a1.getIsCancelled( ), a2.getIsCancelled( ) ) );
                break;
            default:
                stream = sortedList.stream( ).sorted( ( a1, a2 ) -> a1.getStartingDateTime( ).compareTo( a2.getStartingDateTime( ) ) );
        }
        sortedList = stream.collect( Collectors.toList( ) );
        if ( !_filter.isOrderAsc( ) )
        {
            Collections.reverse( sortedList );
        }
        return sortedList;
    }

    /**
     * Get the URL to display the form of a workflow action. If the action has no form, then the user is redirected to the page to execute the workflow action
     *
     * @param request
     *            The request
     * @param strIdAppointment
     *            The id of the appointment
     * @param strIdAction
     *            The id of the workflow action
     * @return The URL
     */
    public static String getUrlExecuteWorkflowAction( HttpServletRequest request, String strIdAppointment, String strIdAction )
    {
        UrlItem url = new UrlItem( AppPathService.getBaseUrl( request ) + JSP_MANAGE_APPOINTMENTS );
        url.addParameter( MVCUtils.PARAMETER_VIEW, VIEW_WORKFLOW_ACTION_FORM );
        url.addParameter( PARAMETER_ID_APPOINTMENT, strIdAppointment );
        url.addParameter( PARAMETER_ID_ACTION, strIdAction );

        return url.getUrl( );
    }

    /**
     * Get the workflow action form before processing the action. If the action does not need to display any form, then redirect the user to the workflow action
     * processing page.
     *
     * @param request
     *            The request
     * @return The HTML content to display, or the next URL to redirect the user to
     */
    @View( VIEW_WORKFLOW_ACTION_FORM )
    public synchronized String getWorkflowActionForm( HttpServletRequest request )
    {
        String strIdAction = request.getParameter( PARAMETER_ID_ACTION );
        String strIdAppointment = request.getParameter( PARAMETER_ID_APPOINTMENT );
        if ( StringUtils.isNotEmpty( strIdAction ) && StringUtils.isNumeric( strIdAction ) && StringUtils.isNotEmpty( strIdAppointment )
                && StringUtils.isNumeric( strIdAppointment ) )
        {
            int nIdAction = Integer.parseInt( strIdAction );
            int nIdAppointment = Integer.parseInt( strIdAppointment );
            if ( WorkflowService.getInstance( ).isDisplayTasksForm( nIdAction, getLocale( ) ) )
            {
                ITaskService taskService = SpringContextService.getBean( TaskService.BEAN_SERVICE );
                List<ITask> listActionTasks = taskService.getListTaskByIdAction( nIdAction, getLocale( ) );
                if ( listActionTasks.stream( ).anyMatch( task -> task.getTaskType( ).getKey( ).equals( "taskReportAppointment" ) )
                /*
                 * && WorkflowService.getInstance( ).canProcessAction( nIdAppointment, Appointment.APPOINTMENT_RESOURCE_TYPE, nIdAction, nExternalParentId,
                 * request, false, null )
                 */ )
                {
                    AppointmentDTO appointment = AppointmentService.buildAppointmentDTOFromIdAppointment( nIdAppointment );
                    Map<String, String> additionalParameters = new HashMap<>( );
                    additionalParameters.put( PARAMETER_ID_APPOINTMENT, strIdAppointment );
                    additionalParameters.put( PARAMETER_NB_PLACE_TO_TAKE, String.valueOf( appointment.getNbBookedSeats( ) ) );
                    additionalParameters.put( PARAMETER_ID_FORM, String.valueOf( appointment.getIdForm( ) ) );

                    return redirect( request, VIEW_CALENDAR_MANAGE_APPOINTMENTS, additionalParameters );
                }
                String strHtmlTasksForm = WorkflowService.getInstance( ).getDisplayTasksForm( nIdAppointment, Appointment.APPOINTMENT_RESOURCE_TYPE, nIdAction,
                        request, getLocale( ), null );
                Map<String, Object> model = new HashMap<>( );
                model.put( MARK_TASKS_FORM, strHtmlTasksForm );
                model.put( PARAMETER_ID_ACTION, nIdAction );
                model.put( PARAMETER_ID_APPOINTMENT, nIdAppointment );
                return getPage( PROPERTY_PAGE_TITLE_TASKS_FORM_WORKFLOW, TEMPLATE_TASKS_FORM_WORKFLOW, model );
            }
            return doProcessWorkflowAction( request );
        }
        return redirect( request, AppointmentFormJspBean.getURLManageAppointmentForms( request ) );
    }

    /**
     * Do process a workflow action over an appointment
     *
     * @param request
     *            The request
     * @return The next URL to redirect to
     */
    @Action( ACTION_DO_PROCESS_WORKFLOW_ACTION )
    public synchronized String doProcessWorkflowAction( HttpServletRequest request )
    {
        String strIdAction = request.getParameter( PARAMETER_ID_ACTION );
        String strIdAppointment = request.getParameter( PARAMETER_ID_APPOINTMENT );
        if ( StringUtils.isNotEmpty( strIdAction ) && StringUtils.isNumeric( strIdAction ) && StringUtils.isNotEmpty( strIdAppointment )
                && StringUtils.isNumeric( strIdAppointment ) )
        {
            int nIdAction = Integer.parseInt( strIdAction );
            int nIdAppointment = Integer.parseInt( strIdAppointment );
            Appointment appointment = AppointmentService.findAppointmentById( nIdAppointment );

            List<AppointmentSlot> listApptSlot = appointment.getListAppointmentSlot( );
            Slot slot = SlotService.findSlotById( listApptSlot.get( 0 ).getIdSlot( ) );

            if ( request.getParameter( PARAMETER_BACK ) == null )
            {
                try
                {
                    if ( WorkflowService.getInstance( ).isDisplayTasksForm( nIdAction, getLocale( ) ) )
                    {
                        String strError = WorkflowService.getInstance( ).doSaveTasksForm( nIdAppointment, Appointment.APPOINTMENT_RESOURCE_TYPE, nIdAction,
                                slot.getIdForm( ), request, getLocale( ), null );
                        if ( strError != null )
                        {
                            return redirect( request, strError );
                        }
                    }
                    else
                    {
                        ITaskService taskService = SpringContextService.getBean( TaskService.BEAN_SERVICE );
                        List<ITask> listActionTasks = taskService.getListTaskByIdAction( nIdAction, getLocale( ) );
                        for ( ITask task : listActionTasks )
                        {
                            if ( task.getTaskType( ).getKey( ).equals( "taskChangeAppointmentStatus" ) && ( appointment.getIsCancelled( ) ) )
                            {
                                for ( AppointmentSlot apptSlt : listApptSlot )
                                {

                                    Slot slt = SlotService.findSlotById( apptSlt.getIdSlot( ) );

                                    if ( apptSlt.getNbPlaces( ) > slt.getNbRemainingPlaces( ) )
                                    {

                                        return redirect( request,
                                                AdminMessageService.getMessageUrl( request, MESSAGE_UNVAILABLE_SLOT, AdminMessage.TYPE_STOP ) );

                                    }
                                }
                            }
                        }

                        WorkflowService.getInstance( ).doProcessAction( nIdAppointment, Appointment.APPOINTMENT_RESOURCE_TYPE, nIdAction, slot.getIdForm( ),
                                request, getLocale( ), false, null );
                        AppointmentListenerManager.notifyAppointmentWFActionTriggered( nIdAppointment, nIdAction );
                    }
                }
                catch( Exception e )
                {
                    AppLogService.error( "Error Workflow", e );
                }
                Map<String, String> mapParams = new HashMap<>( );
                mapParams.put( PARAMETER_ID_FORM, Integer.toString( slot.getIdForm( ) ) );
                return redirect( request, VIEW_MANAGE_APPOINTMENTS, mapParams );
            }
            return redirect( request, VIEW_MANAGE_APPOINTMENTS, PARAMETER_ID_FORM, slot.getIdForm( ) );
        }
        return redirect( request, AppointmentFormJspBean.getURLManageAppointmentForms( request ) );
    }

    /**
     * Do change the status of an appointment
     *
     * @param request
     *            The request
     * @return The next URL to redirect to
     * @throws AccessDeniedException
     *             If the user is not authorized to access this feature
     */
    @Action( ACTION_DO_CHANGE_APPOINTMENT_STATUS )
    public synchronized String doChangeAppointmentStatus( HttpServletRequest request ) throws AccessDeniedException
    {
        String strIdAppointment = request.getParameter( PARAMETER_ID_APPOINTMENT );
        String strStatusCancelled = request.getParameter( PARAMETER_STATUS_CANCELLED );
        if ( StringUtils.isNotEmpty( strIdAppointment ) && StringUtils.isNumeric( strIdAppointment ) && StringUtils.isNotEmpty( strStatusCancelled ) )
        {
            int nIdAppointment = Integer.parseInt( strIdAppointment );
            boolean bStatusCancelled = Boolean.parseBoolean( strStatusCancelled );
            Appointment appointment = AppointmentService.findAppointmentById( nIdAppointment );
            int idSlot = appointment.getListAppointmentSlot( ).get( 0 ).getIdSlot( );
            Slot slot = SlotService.findSlotById( idSlot );
            if ( !RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, Integer.toString( slot.getIdForm( ) ),
                    AppointmentResourceIdService.PERMISSION_CHANGE_APPOINTMENT_STATUS, (User) getUser( ) ) )
            {
                throw new AccessDeniedException( AppointmentResourceIdService.PERMISSION_CHANGE_APPOINTMENT_STATUS );
            }
            if ( appointment.getIsCancelled( ) && !bStatusCancelled )
            {

                for ( AppointmentSlot apptSlot : appointment.getListAppointmentSlot( ) )
                {

                    slot = SlotService.findSlotById( idSlot );

                    if ( ( apptSlot.getNbPlaces( ) > slot.getNbRemainingPlaces( ) ) )
                    {
                        return redirect( request, AdminMessageService.getMessageUrl( request, MESSAGE_UNVAILABLE_SLOT, AdminMessage.TYPE_STOP ) );
                    }

                }
            }
            if ( appointment.getIsCancelled( ) != bStatusCancelled )
            {
                appointment.setIsCancelled( bStatusCancelled );
                AppointmentService.updateAppointment( appointment );
                AppLogService.info( LogUtilities.buildLog( ACTION_DO_CHANGE_APPOINTMENT_STATUS, strIdAppointment, getUser( ) ) );
            }
            return redirect( request, VIEW_MANAGE_APPOINTMENTS, PARAMETER_ID_FORM, slot.getIdForm( ) );
        }
        return redirect( request, AppointmentFormJspBean.getURLManageAppointmentForms( request ) );
    }

    /**
     * List of all the available status of an appointment
     *
     * @return the list of the status
     */
    private ReferenceList getListStatus( )
    {
        ReferenceList refListStatus = new ReferenceList( );
        refListStatus.addItem( -1, StringUtils.EMPTY );
        refListStatus.addItem( 0, I18nService.getLocalizedString( RESERVED, getLocale( ) ) );
        refListStatus.addItem( 1, I18nService.getLocalizedString( UNRESERVED, getLocale( ) ) );
        return refListStatus;
    }

    private List<AppointmentDTO> findListAppointmentsDTOByFilterByPage( )
    {
        int currentPage;
        try
        {
            currentPage = Integer.parseInt( _strCurrentPageIndex );
        }
        catch( NumberFormatException ex )
        {
            currentPage = 1;
        }

        int skip = ( currentPage - 1 ) * _nItemsPerPage;

        List<Integer> listIdAppointment = _filter.getListIdAppointment( );

        _filter.setListIdAppointment( _listAppointmentsIds.stream( ).skip( skip ).limit( _nItemsPerPage ).collect( Collectors.toList( ) ) );

        List<AppointmentDTO> listAppointmentsDTO = AppointmentService.findListAppointmentsDTOByFilter( _filter );

        _filter.setListIdAppointment( listIdAppointment );

        return listAppointmentsDTO;
    }

}
