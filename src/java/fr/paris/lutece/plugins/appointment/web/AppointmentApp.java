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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;

import fr.paris.lutece.portal.business.file.File;
import fr.paris.lutece.portal.service.security.SecurityTokenService;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import fr.paris.lutece.plugins.appointment.business.appointment.Appointment;
import fr.paris.lutece.plugins.appointment.business.appointment.AppointmentSlot;
import fr.paris.lutece.plugins.appointment.business.calendar.CalendarTemplate;
import fr.paris.lutece.plugins.appointment.business.calendar.CalendarTemplateHome;
import fr.paris.lutece.plugins.appointment.business.form.Form;
import fr.paris.lutece.plugins.appointment.business.message.FormMessage;
import fr.paris.lutece.plugins.appointment.business.planning.WeekDefinition;
import fr.paris.lutece.plugins.appointment.business.rule.ReservationRule;
import fr.paris.lutece.plugins.appointment.business.slot.Slot;
import fr.paris.lutece.plugins.appointment.exception.AppointmentSavedException;
import fr.paris.lutece.plugins.appointment.exception.SlotEditTaskExpiredTimeException;
import fr.paris.lutece.plugins.appointment.exception.SlotFullException;
import fr.paris.lutece.plugins.appointment.log.LogUtilities;
import fr.paris.lutece.plugins.appointment.service.AppointmentResponseService;
import fr.paris.lutece.plugins.appointment.service.AppointmentService;
import fr.paris.lutece.plugins.appointment.service.AppointmentUtilities;
import fr.paris.lutece.plugins.appointment.service.EntryService;
import fr.paris.lutece.plugins.appointment.service.FormMessageService;
import fr.paris.lutece.plugins.appointment.service.FormService;
import fr.paris.lutece.plugins.appointment.service.ReservationRuleService;
import fr.paris.lutece.plugins.appointment.service.SlotSafeService;
import fr.paris.lutece.plugins.appointment.service.SlotService;
import fr.paris.lutece.plugins.appointment.service.UserService;
import fr.paris.lutece.plugins.appointment.service.Utilities;
import fr.paris.lutece.plugins.appointment.service.WeekDefinitionService;
import fr.paris.lutece.plugins.appointment.service.listeners.AppointmentListenerManager;
import fr.paris.lutece.plugins.appointment.service.upload.AppointmentAsynchronousUploadHandler;
import fr.paris.lutece.plugins.appointment.web.dto.AppointmentDTO;
import fr.paris.lutece.plugins.appointment.web.dto.AppointmentFilterDTO;
import fr.paris.lutece.plugins.appointment.web.dto.AppointmentFormDTO;
import fr.paris.lutece.plugins.genericattributes.business.Entry;
import fr.paris.lutece.plugins.genericattributes.business.GenericAttributeError;
import fr.paris.lutece.plugins.genericattributes.business.Response;
import fr.paris.lutece.plugins.genericattributes.service.file.GenericAttributeFileService;
import fr.paris.lutece.plugins.workflowcore.service.task.ITask;
import fr.paris.lutece.plugins.workflowcore.service.task.ITaskService;
import fr.paris.lutece.plugins.workflowcore.service.task.TaskService;
import fr.paris.lutece.portal.service.accesscontrol.AccessControlService;
import fr.paris.lutece.portal.service.admin.AccessDeniedException;
import fr.paris.lutece.portal.service.captcha.CaptchaSecurityService;
import fr.paris.lutece.portal.service.i18n.I18nService;
import fr.paris.lutece.portal.service.image.ImageResource;
import fr.paris.lutece.portal.service.message.SiteMessageException;
import fr.paris.lutece.portal.service.security.LuteceUser;
import fr.paris.lutece.portal.service.security.SecurityService;
import fr.paris.lutece.portal.service.security.UserNotSignedException;
import fr.paris.lutece.portal.service.spring.SpringContextService;
import fr.paris.lutece.portal.service.template.AppTemplateService;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPathService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import fr.paris.lutece.portal.service.workflow.WorkflowService;
import fr.paris.lutece.portal.util.mvc.commons.annotations.Action;
import fr.paris.lutece.portal.util.mvc.commons.annotations.View;
import fr.paris.lutece.portal.util.mvc.utils.MVCMessage;
import fr.paris.lutece.portal.util.mvc.utils.MVCUtils;
import fr.paris.lutece.portal.util.mvc.xpage.MVCApplication;
import fr.paris.lutece.portal.util.mvc.xpage.annotations.Controller;
import fr.paris.lutece.portal.web.xpages.XPage;
import fr.paris.lutece.util.ErrorMessage;
import fr.paris.lutece.util.html.HtmlTemplate;
import fr.paris.lutece.util.url.UrlItem;

/**
 * This class provides a simple implementation of an Appointment XPage (On Front Office)
 *
 * @author Laurent Payen
 *
 */
@Controller( xpageName = AppointmentApp.XPAGE_NAME, pageTitleI18nKey = AppointmentApp.MESSAGE_DEFAULT_PAGE_TITLE, pagePathI18nKey = AppointmentApp.MESSAGE_DEFAULT_PATH )
public class AppointmentApp extends MVCApplication
{

    /**
     * Default page of XPages of this app
     */
    public static final String MESSAGE_DEFAULT_PATH = "appointment.appointment.name";

    /**
     * Default page title of XPages of this app
     */
    public static final String MESSAGE_DEFAULT_PAGE_TITLE = "appointment.appointmentApp.defaultTitle";

    /**
     * The name of the XPage
     */
    protected static final String XPAGE_NAME = "appointment";

    /**
     * Generated serial version UID
     */
    private static final long serialVersionUID = 5741361182728887387L;

    // Templates
    private static final String TEMPLATE_APPOINTMENT_FORM_LIST = "/skin/plugins/appointment/appointment_form_list.html";
    private static final String TEMPLATE_APPOINTMENT_FORM = "/skin/plugins/appointment/appointment_form.html";
    private static final String TEMPLATE_APPOINTMENT_FORM_RECAP = "/skin/plugins/appointment/appointment_form_recap.html";
    private static final String TEMPLATE_APPOINTMENT_CREATED = "skin/plugins/appointment/appointment_created.html";
    private static final String TEMPLATE_CANCEL_APPOINTMENT = "skin/plugins/appointment/cancel_appointment.html";
    private static final String TEMPLATE_APPOINTMENT_CANCELED = "skin/plugins/appointment/appointment_canceled.html";
    private static final String TEMPLATE_MY_APPOINTMENTS = "skin/plugins/appointment/my_appointments.html";
    private static final String TEMPLATE_HTML_CODE_FORM = "skin/plugins/appointment/html_code_form.html";
    private static final String TEMPLATE_HTML_CODE_NB_PLACES_TO_TAKE_FORM = "skin/plugins/appointment/appointment_nb_places_to_take_form.html";
    private static final String TEMPLATE_TASKS_FORM_WORKFLOW = "skin/plugins/appointment/tasks_form_workflow.html";
    private static final String TEMPLATE_ERROR_APPOINTMENT_REFERENCE = "skin/plugins/appointment/error_appointment_reference.html";

    // Views
    public static final String VIEW_APPOINTMENT_FORM = "getViewAppointmentForm";
    public static final String VIEW_APPOINTMENT_CALENDAR = "getViewAppointmentCalendar";
    private static final String VIEW_APPOINTMENT_FORM_LIST = "getViewFormList";
    private static final String VIEW_DISPLAY_RECAP_APPOINTMENT = "displayRecapAppointment";
    private static final String VIEW_GET_APPOINTMENT_CREATED = "getAppointmentCreated";
    private static final String VIEW_APPOINTMENT_CANCELED = "getAppointmentCanceled";
    private static final String VIEW_GET_MY_APPOINTMENTS = "getMyAppointments";
    private static final String VIEW_GET_VIEW_CANCEL_APPOINTMENT = "getViewCancelAppointment";
    private static final String VIEW_WORKFLOW_ACTION_FORM = "viewWorkflowActionForm";
    private static final String VIEW_CHANGE_DATE_APPOINTMENT = "viewChangeDateAppointment";

    // Actions
    private static final String ACTION_DO_VALIDATE_FORM = "doValidateForm";
    private static final String ACTION_DO_MAKE_APPOINTMENT = "doMakeAppointment";
    private static final String ACTION_DO_CANCEL_APPOINTMENT = "doCancelAppointment";
    private static final String ACTION_DO_PROCESS_WORKFLOW_ACTION = "doProcessWorkflowAction";

    // Parameters
    private static final String PARAMETER_STARTING_DATE_TIME = "starting_date_time";
    private static final String PARAMETER_ENDING_DATE_OF_DISPLAY = "ending_date_of_display";
    private static final String PARAMETER_STR_ENDING_DATE_OF_DISPLAY = "str_ending_date_of_display";
    private static final String PARAMETER_DATE_OF_DISPLAY = "date_of_display";
    private static final String PARAMETER_DAY_OF_WEEK = "dow";
    private static final String PARAMETER_HIDDEN_DAYS = "hidden_days";
    private static final String PARAMETER_ID_FORM = "id_form";
    private static final String PARAMETER_EVENTS = "events";
    private static final String PARAMETER_MIN_DURATION = "min_duration";
    private static final String PARAMETER_MIN_TIME = "min_time";
    private static final String PARAMETER_MAX_TIME = "max_time";
    private static final String PARAMETER_EMAIL = "email";
    private static final String PARAMETER_EMAIL_CONFIRMATION = "emailConfirm";
    private static final String PARAMETER_FIRST_NAME = "firstname";
    private static final String PARAMETER_LAST_NAME = "lastname";
    private static final String PARAMETER_NUMBER_OF_BOOKED_SEATS = "nbBookedSeats";
    private static final String PARAMETER_BACK = "back";
    private static final String PARAMETER_REF_APPOINTMENT = "refAppointment";
    private static final String PARAMETER_FROM_MY_APPOINTMENTS = "fromMyappointments";
    private static final String PARAMETER_REFERER = "referer";
    private static final String PARAMETER_WEEK_VIEW = "week_view";
    private static final String PARAMETER_DAY_VIEW = "day_view";
    private static final String PARAMETER_ANCHOR = "anchor";
    private static final String PARAMETER_MODIFICATION_FORM = "mod";
    private static final String PARAMETER_MIN_DATE_OF_OPEN_DAY = "min_date_of_open_day";
    private static final String PARAMETER_MAX_DATE_OF_OPEN_DAY = "max_date_of_open_day";
    private static final String PARAMETER_IS_MODIFICATION = "is_modification";
    private static final String PARAMETER_NB_PLACE_TO_TAKE = "nbPlacesToTake";
    private static final String PARAMETER_ID_ACTION = "id_action";
    private static final String PARAMETER_MODIF_DATE = "modif_date";

    // Mark
    private static final String MARK_MODIFICATION_DATE_APPOINTMENT = "modifDateAppointment";
    private static final String MARK_NBPLACESTOTAKE = "nbPlacesToTake";
    private static final String MARK_MAX_NBPLACESTOTAKE = "maxNbPlacesToTake";
    private static final String MARK_INFOS = "infos";
    private static final String MARK_LOCALE = "locale";
    private static final String MARK_FORM = "form";
    private static final String MARK_USER = "user";
    private static final String MARK_FORM_MESSAGES = "formMessages";
    private static final String MARK_STR_ENTRY = "str_entry";
    private static final String MARK_APPOINTMENT = "appointment";
    private static final String MARK_LIST_ERRORS = "listAllErrors";
    private static final String MARK_PLACES = "nbplaces";
    private static final String MARK_DATE_APPOINTMENT = "dateAppointment";
    private static final String MARK_STARTING_TIME_APPOINTMENT = "startingTimeAppointment";
    private static final String MARK_ENDING_TIME_APPOINTMENT = "endingTimeAppointment";
    private static final String MARK_FORM_LIST = "form_list";
    private static final String MARK_FORM_HTML = "form_html";
    private static final String MARK_FORM_NB_PLACES_TO_TAKE_HTML = "mark_nb_places_to_take_form";
    private static final String MARK_FORM_ERRORS = "form_errors";
    private static final String MARK_FORM_CALENDAR_ERRORS = "formCalendarErrors";
    private static final String MARK_CAPTCHA = "captcha";
    private static final String MARK_REF = "%%REF%%";
    private static final String MARK_DATE_APP = "%%DATE%%";
    private static final String MARK_TIME_BEGIN = "%%HEURE_DEBUT%%";
    private static final String MARK_TIME_END = "%%HEURE_FIN%%";
    private static final String MARK_LIST_APPOINTMENTS = "list_appointments";
    private static final String MARK_BACK_URL = "backUrl";
    private static final String MARK_FROM_URL = "fromUrl";
    private static final String MARK_LIST_RESPONSE_RECAP_DTO = "listResponseRecapDTO";
    private static final String MARK_DATA = "data";
    private static final String MARK_BASE_64 = "base64";
    private static final String MARK_SEMI_COLON = ";";
    private static final String MARK_COMMA = ",";
    private static final String MARK_COLON = ":";
    private static final String MARK_ICONS = "icons";
    private static final String MARK_ICON_NULL = "NULL";
    private static final String MARK_ANCHOR = "#";
    private static final String MARK_APPOINTMENT_ALREADY_CANCELLED = "alreadyCancelled";
    private static final String MARK_NO_APPOINTMENT_WITH_THIS_REFERENCE = "noAppointmentWithThisReference";
    private static final String MARK_APPOINTMENT_PASSED = "appointmentPassed";
    private static final String MARK_TASKS_FORM = "tasks_form";
    private static final String MARK_LOCALE_DATE_TIME = "localeDateTime";
    private static final String MARK_USER_PREFERRED_NAME = "preferred_user_name";

    // Errors
    private static final String ERROR_MESSAGE_SLOT_FULL = "appointment.message.error.slotFull";
    private static final String ERROR_MESSAGE_SLOT_EDIT_TASK_EXPIRED_TIME = "appointment.message.error.appointment.edit.expired.time";
    private static final String ERROR_MESSAGE_CAPTCHA = "portal.admin.message.wrongCaptcha";
    private static final String ERROR_MESSAGE_NB_MIN_DAYS_BETWEEN_TWO_APPOINTMENTS = "appointment.validation.appointment.NbMinDaysBetweenTwoAppointments.error";
    private static final String ERROR_MESSAGE_NB_MAX_APPOINTMENTS_ON_A_PERIOD = "appointment.validation.appointment.NbMaxAppointmentsOnAPeriod.error";
    private static final String ERROR_MESSAGE_NB_MAX_APPOINTMENTS_ON_A_CATEGORY = "appointment.validation.appointment.NbMaxAppointmentsOnCategory.error";
    private static final String ERROR_MESSAGE_FORM_NOT_ACTIVE = "appointment.validation.appointment.formNotActive";
    private static final String ERROR_MESSAGE_NO_STARTING_VALIDITY_DATE = "appointment.validation.appointment.noStartingValidityDate";
    private static final String ERROR_MESSAGE_FORM_NO_MORE_VALID = "appointment.validation.appointment.formNoMoreValid";
    private static final String ERROR_MESSAGE_REPORT_APPOINTMENT = "appointment.message.error.report.appointment";
    private static final String ERROR_MESSAGE_NO_AVAILABLE_SLOT = "appointment.validation.appointment.noAvailableSlot";

    private static final String ERROR_MESSAGE_NB_PLACE_TO_TAKE_TO_BIG = "appointment.message.error.nbplacestotake.toobig";

    private static final String MESSAGE_ERROR_TOKEN = "Invalid security token";

    // Messages
    private static final String MESSAGE_CANCEL_APPOINTMENT_PAGE_TITLE = "appointment.cancelAppointment.pageTitle";
    private static final String MESSAGE_MY_APPOINTMENTS_PAGE_TITLE = "appointment.myAppointments.name";
    private static final String MESSAGE_WF_ACTION_SUCESS = "appointment.wf.action.success";

    // Properties
    private static final String PROPERTY_USER_ATTRIBUTE_FIRST_NAME = "appointment.userAttribute.firstName";
    private static final String PROPERTY_USER_ATTRIBUTE_LAST_NAME = "appointment.userAttribute.lastName";
    private static final String PROPERTY_USER_ATTRIBUTE_PREFERED_NAME = "appointment.userAttribute.preferred_username";
    private static final String PROPERTY_USER_ATTRIBUTE_EMAIL = "appointment.userAttribute.email";
    private static final String AGENDA_WEEK = "agendaWeek";
    private static final String BASIC_WEEK = "basicWeek";
    private static final String AGENDA_DAY = "agendaDay";
    private static final String BASIC_DAY = "basicDay";
    private static final String STEP_3 = "step3";

    // Local variables
    private transient CaptchaSecurityService _captchaSecurityService;
    private int _nNbPlacesToTake;
    private String _strNbPlacesToTakeLength;
    private AppointmentFormDTO _appointmentForm;
    private AppointmentDTO _notValidatedAppointment;
    private AppointmentDTO _validatedAppointment;

    /**
     * Get the calendar view
     *
     * @param request
     * @return the Xpage
     * @throws AccessDeniedException
     */
    @SuppressWarnings( "unchecked" )
    @View( VIEW_APPOINTMENT_CALENDAR )
    public synchronized XPage getViewAppointmentCalendar( HttpServletRequest request ) throws AccessDeniedException
    {
        Map<String, Object> model = getModel( );
        Locale locale = getLocale( request );
        _nNbPlacesToTake = 0;

        int nIdForm = Integer.parseInt( request.getParameter( PARAMETER_ID_FORM ) );
        String nbPlacesToTake = request.getParameter( PARAMETER_NB_PLACE_TO_TAKE );
        String refAppointment = request.getParameter( PARAMETER_REF_APPOINTMENT );

        _appointmentForm = FormService.buildAppointmentFormWithoutReservationRule( nIdForm );
        _strNbPlacesToTakeLength = String.valueOf(_appointmentForm.getNbConsecutiveSlots());
        boolean bError = false;
        if ( !_appointmentForm.getIsActive( ) )
        {
            addError( ERROR_MESSAGE_FORM_NOT_ACTIVE, locale );
            bError = true;
        }

        FormMessage formMessages = FormMessageService.findFormMessageByIdForm( nIdForm );

        if ( StringUtils.isNotEmpty( refAppointment ) )
        {
            // If we want to change the date of an appointment
            AppointmentDTO appointmentDTO = AppointmentService.buildAppointmentDTOFromRefAppointment( refAppointment );
            // Check if an appointment Object was found and built with the given reference
            if( appointmentDTO == null )
            {
            	// When the appointment Object doesn't exist, we display an error message to the user
            	return getXPage( TEMPLATE_ERROR_APPOINTMENT_REFERENCE, locale, model );
            }
            if ( appointmentDTO.getIsCancelled( ) || appointmentDTO.getStartingDateTime( ).isBefore( LocalDateTime.now( ) ) )
            {
                addError( ERROR_MESSAGE_REPORT_APPOINTMENT, locale );
                bError = true;
            }
            else
            {
                _validatedAppointment = appointmentDTO;
                AppointmentService.addAppointmentResponses( _validatedAppointment );
                nbPlacesToTake = Integer.toString( _validatedAppointment.getNbBookedSeats( ) );
            }
        }
        LocalDate startingDateOfDisplay = LocalDate.now( );
        // Check if the date of display and the endDateOfDisplay are in the
        // validity date range of the form
        LocalDate startingValidityDate = null;
        if ( _appointmentForm.getDateStartValidity( ) == null )
        {
            addError( ERROR_MESSAGE_NO_STARTING_VALIDITY_DATE, locale );
            bError = true;
        }
        else
        {
            startingValidityDate = _appointmentForm.getDateStartValidity( ).toLocalDate( );
        }
        if ( startingValidityDate != null && startingValidityDate.isAfter( startingDateOfDisplay ) )
        {
            startingDateOfDisplay = startingValidityDate;
        }
        // Get the nb weeks to display
        int nNbWeeksToDisplay = _appointmentForm.getNbWeeksToDisplay( );
        // Calculate the ending date of display with the nb weeks to display
        // since today
        // We calculate the number of weeks including the current week, so it
        // will end to the (n) next sunday
        TemporalField fieldISO = WeekFields.of( locale ).dayOfWeek( );
        LocalDate dateOfSunday = startingDateOfDisplay.with( fieldISO, DayOfWeek.SUNDAY.getValue( ) );
        LocalDate endingDateOfDisplay = dateOfSunday.plusWeeks( (long) nNbWeeksToDisplay - 1 );
        // if the ending date of display is after the ending validity date of
        // the form
        // assign the ending date of display with the ending validity date of
        // the form
        LocalDate endingValidityDate = null;
        if ( _appointmentForm.getDateEndValidity( ) != null )
        {
            endingValidityDate = _appointmentForm.getDateEndValidity( ).toLocalDate( );
            if ( endingDateOfDisplay.isAfter( endingValidityDate ) )
            {
                endingDateOfDisplay = endingValidityDate;
            }
            if ( startingDateOfDisplay.isAfter( endingDateOfDisplay ) )
            {
                addError( ERROR_MESSAGE_FORM_NO_MORE_VALID, locale );
                bError = true;
            }
        }
        // Get the current date of display of the calendar, if it exists
        String strDateOfDisplay = request.getParameter( PARAMETER_DATE_OF_DISPLAY );
        LocalDate dateOfDisplay = startingDateOfDisplay;
        if ( StringUtils.isNotEmpty( strDateOfDisplay ) )
        {
            dateOfDisplay = LocalDate.parse( strDateOfDisplay );
        }
        // Get all the week definitions
        List<WeekDefinition> listWeekDefinition = WeekDefinitionService.findWeekDefinitionByDateOfApply( nIdForm, startingDateOfDisplay, endingDateOfDisplay );

        Map<WeekDefinition, ReservationRule> mapReservationRule = ReservationRuleService.findAllReservationRule( nIdForm, listWeekDefinition );
        List<ReservationRule> listReservationRules = new ArrayList<>( mapReservationRule.values( ) );
        // Get the min time of all the week definitions
        LocalTime minStartingTime = WeekDefinitionService.getMinStartingTimeOfAListOfWeekDefinition( listReservationRules );
        // Get the max time of all the week definitions
        LocalTime maxEndingTime = WeekDefinitionService.getMaxEndingTimeOfAListOfWeekDefinition( listReservationRules );
        // Get all the working days of all the week definitions

        List<String> listStrBase0OpenDaysOfWeek = new ArrayList<>(
                WeekDefinitionService.getSetDaysOfWeekOfAListOfWeekDefinitionForFullCalendar( listReservationRules ) );
        // Build the slots if no errors
        List<Slot> listSlots = new ArrayList<>( );
        if ( !bError )
        {
            boolean isNewNbPlacesToTake = ( nbPlacesToTake != null && StringUtils.isNumeric( nbPlacesToTake ) );
            if ( _appointmentForm.getIsMultislotAppointment( ) && ( _nNbPlacesToTake != 0 || isNewNbPlacesToTake ) )
            {
                _nNbPlacesToTake = isNewNbPlacesToTake ? Integer.parseInt( nbPlacesToTake ) : _nNbPlacesToTake;
                listSlots = SlotService.buildListSlot( nIdForm, mapReservationRule, startingDateOfDisplay, endingDateOfDisplay, _nNbPlacesToTake );

            }
            else
            {
                _nNbPlacesToTake = 0;
                listSlots = SlotService.buildListSlot( nIdForm, mapReservationRule, startingDateOfDisplay, endingDateOfDisplay );
            }

            if ( _nNbPlacesToTake > Integer.parseInt( _strNbPlacesToTakeLength ) )
            {
            	addError( ERROR_MESSAGE_NB_PLACE_TO_TAKE_TO_BIG, locale );
            }

            // Check the Access Controls of the form, when displaying the calendar
            XPage accessControlPage = AccessControlService.getInstance( ).doExecuteAccessControl(
            		request,
            		nIdForm,
            		Form.RESOURCE_TYPE,
            		null
            		);
            if ( accessControlPage != null )
            {
            	return accessControlPage;
            }

            // Get the min time from now before a user can take an appointment (in hours)
            int minTimeBeforeAppointment = _appointmentForm.getMinTimeBeforeAppointment( );
            LocalDateTime dateTimeBeforeAppointment = LocalDateTime.now( ).plusHours( minTimeBeforeAppointment );
            // Filter the list of slots
            if ( CollectionUtils.isNotEmpty( listSlots ) )
            {
                listSlots = listSlots.stream( ).filter( s -> s.getStartingDateTime( ).isAfter( dateTimeBeforeAppointment ) ).collect( Collectors.toList( ) );
            }

            // If we change the date of an appointment
            // filter the list of slot with only the ones that have enough places at
            // the moment of the edition
            if ( _validatedAppointment != null )
            {
                int nbBookedSeats = _validatedAppointment.getNbBookedSeats( );
                listSlots = listSlots.stream( ).filter( s -> s.getNbPotentialRemainingPlaces( ) >= nbBookedSeats && s.getIsOpen( ) )
                        .collect( Collectors.toList( ) );
                model.put( MARK_MODIFICATION_DATE_APPOINTMENT, true );
                model.put( PARAMETER_REF_APPOINTMENT, refAppointment );
            }
            else
            {
                model.put( MARK_MODIFICATION_DATE_APPOINTMENT, false );
            }

            LocalDate firstDateOfFreeOpenSlot = null;
            if ( CollectionUtils.isNotEmpty( listSlots ) )
            {
                // Need to find the first available slot from now (with time)
                List<Slot> listAvailableSlots = listSlots.stream( ).filter( s -> ( s.getNbPotentialRemainingPlaces( ) > 0 && s.getIsOpen( ) == Boolean.TRUE ) )
                        .collect( Collectors.toList( ) );
                if ( CollectionUtils.isNotEmpty( listAvailableSlots ) )
                {
                    firstDateOfFreeOpenSlot = listAvailableSlots.stream( ).min( ( s1, s2 ) -> s1.getStartingDateTime( ).compareTo( s2.getStartingDateTime( ) ) )
                            .get( ).getDate( );
                }
            }
            if (firstDateOfFreeOpenSlot == null) {
                if (formMessages != null && StringUtils.isNotEmpty(formMessages.getNoAvailableSlot())) {
                    addError(formMessages.getNoAvailableSlot());
                } else {
                    addError(ERROR_MESSAGE_NO_AVAILABLE_SLOT, locale);
                }
                bError = true;
            }
            // Display the week with the first available slot
            if ( firstDateOfFreeOpenSlot != null && firstDateOfFreeOpenSlot.isAfter( dateOfDisplay ) )
            {
                dateOfDisplay = firstDateOfFreeOpenSlot;
            }
        }
        if ( bError )
        {
            model.put( MARK_FORM_CALENDAR_ERRORS, bError );
        }
        if ( formMessages != null && StringUtils.isNotEmpty( formMessages.getCalendarDescription( ) ) )
        {
            List<ErrorMessage> listInfos = (List<ErrorMessage>) model.get( MARK_INFOS );
            if ( listInfos == null )
            {
                listInfos = new ArrayList<>( );
                model.put( MARK_INFOS, listInfos );
            }
            MVCMessage message = new MVCMessage( formMessages.getCalendarDescription( ) );
            listInfos.add( message );
        }

        CalendarTemplate calendarTemplate = CalendarTemplateHome.findByPrimaryKey( _appointmentForm.getCalendarTemplateId( ) );
        List<String> listHiddenDays = Stream.of( "0", "1", "2", "3", "4", "5", "6" ).collect( Collectors.toList( ) );

        /**
         * Calculate the hidden days and set the view (Day and week) with the type of calendar
         */
        String dayView = null;
        String weekView = null;
        switch( calendarTemplate.getTitle( ) )
        {
            case CalendarTemplate.FREE_SLOTS:
                // Keep only the available slots
                listSlots = listSlots.stream( ).filter( s -> ( ( s.getNbRemainingPlaces( ) > 0 ) && ( s.getIsOpen( ) ) ) ).collect( Collectors.toList( ) );
                listHiddenDays.clear( );
                dayView = BASIC_DAY;
                weekView = BASIC_WEEK;
                break;
            case CalendarTemplate.FREE_SLOTS_GROUPED:
                // Keep only the available slots
                listHiddenDays.clear( );
                dayView = BASIC_DAY;
                weekView = BASIC_WEEK;
                break;
            case CalendarTemplate.CALENDAR_OPEN_DAYS:
                // update the list of the days to hide
                listHiddenDays.removeAll( listStrBase0OpenDaysOfWeek );
                dayView = AGENDA_DAY;
                weekView = AGENDA_WEEK;
                break;
            case CalendarTemplate.FREE_SLOTS_ON_OPEN_DAYS:
                // Keep only the available slots
                listSlots = listSlots.stream( ).filter( s -> ( ( s.getNbRemainingPlaces( ) > 0 ) && ( s.getIsOpen( ) ) ) ).collect( Collectors.toList( ) );
                // update the list of the days to hide
                listHiddenDays.removeAll( listStrBase0OpenDaysOfWeek );
                dayView = BASIC_DAY;
                weekView = BASIC_WEEK;
                break;
            case CalendarTemplate.CALENDAR:
            default:
                listHiddenDays.clear( );
                dayView = AGENDA_DAY;
                weekView = AGENDA_WEEK;
                break;
        }
        // Get the min and max date of the open days (for the week navigation on
        // open days calendar templates)
        Set<Integer> setOpenDays = WeekDefinitionService.getOpenDaysOfWeek( listReservationRules );
        model.put( PARAMETER_MIN_DATE_OF_OPEN_DAY,
                LocalDate.now( ).with( DayOfWeek.of( setOpenDays.stream( ).min( Comparator.naturalOrder( ) ).orElse( 1 ) ) ) );
        model.put( PARAMETER_MAX_DATE_OF_OPEN_DAY,
                endingDateOfDisplay.with( DayOfWeek.of( setOpenDays.stream( ).max( Comparator.naturalOrder( ) ).orElse( 1 ) ) ) );

        model.put( MARK_FORM, _appointmentForm );
        model.put( PARAMETER_ID_FORM, nIdForm );
        model.put( MARK_FORM_MESSAGES, formMessages );
        model.put( PARAMETER_ENDING_DATE_OF_DISPLAY, endingDateOfDisplay );
        model.put( PARAMETER_STR_ENDING_DATE_OF_DISPLAY, endingDateOfDisplay.format( Utilities.getFormatter( ) ) );
        model.put( PARAMETER_DATE_OF_DISPLAY, dateOfDisplay );
        model.put( PARAMETER_DAY_OF_WEEK, listStrBase0OpenDaysOfWeek );
        model.put( PARAMETER_MIN_TIME, AppointmentUtilities.getMinTimeToDisplay( minStartingTime ) );
        model.put( PARAMETER_MAX_TIME, AppointmentUtilities.getMaxTimeToDisplay( maxEndingTime ) );
        model.put( PARAMETER_MIN_DURATION, LocalTime.MIN.plusMinutes( AppointmentUtilities.THIRTY_MINUTES ) );
        model.put( MARK_NBPLACESTOTAKE, _nNbPlacesToTake );
        model.put( PARAMETER_EVENTS, listSlots );
        model.put( PARAMETER_HIDDEN_DAYS, listHiddenDays );
        model.put( PARAMETER_DAY_VIEW, dayView );
        model.put( PARAMETER_WEEK_VIEW, weekView );
        model.put( MARK_MAX_NBPLACESTOTAKE, Integer.valueOf( _strNbPlacesToTakeLength ) );
        HtmlTemplate templateNbPlacesToTakeForm = AppTemplateService.getTemplate( TEMPLATE_HTML_CODE_NB_PLACES_TO_TAKE_FORM, locale, model );
        model.put( MARK_FORM_NB_PLACES_TO_TAKE_HTML, templateNbPlacesToTakeForm.getHtml( ) );

        return getXPage( calendarTemplate.getTemplatePath( ), locale, model );
    }

    /**
     * Get the form appointment view (front office)
     *
     * @param request
     *            the request
     * @return the xpage
     * @throws UserNotSignedException
     */
    @View( VIEW_APPOINTMENT_FORM )
    public synchronized XPage getViewAppointmentForm( HttpServletRequest request ) throws UserNotSignedException, AccessDeniedException
    {
        String strIdForm = request.getParameter( PARAMETER_ID_FORM );
        String strNbPlacesToTake = request.getParameter( PARAMETER_NB_PLACE_TO_TAKE );
        String strModifDateAppointment = request.getParameter( PARAMETER_MODIF_DATE );

        if ( strModifDateAppointment != null && Boolean.parseBoolean( strModifDateAppointment ) && _validatedAppointment != null
                && _validatedAppointment.getIdAppointment( ) != 0 )
        {
            return getViewChangeDateAppointment( request );
        }
        if ( strNbPlacesToTake != null )
        {
            _nNbPlacesToTake = Integer.parseInt( strNbPlacesToTake );
        }

        int nIdForm = Integer.parseInt( strIdForm );
        if ( _appointmentForm == null || _appointmentForm.getIdForm( ) != nIdForm )
        {
            _appointmentForm = FormService.buildAppointmentFormWithoutReservationRule( nIdForm );
        }
        if ( !_appointmentForm.getIsActive( ) )
        {
            addError( ERROR_MESSAGE_FORM_NOT_ACTIVE, getLocale( request ) );
            return redirect( request, VIEW_APPOINTMENT_CALENDAR, PARAMETER_ID_FORM, nIdForm, PARAMETER_NB_PLACE_TO_TAKE, _nNbPlacesToTake );
        }
        if(!_appointmentForm.getIsMultislotAppointment())
        {
            _nNbPlacesToTake = 0;
        }
        checkMyLuteceAuthentication( _appointmentForm, request );
        // Patch needed for authentication after being on the form
        String secondAttempt = request.getParameter( "secondAttempt" );
        boolean bTestSecondAttempt = Boolean.FALSE;
        if ( StringUtils.isNotEmpty( secondAttempt ) )
        {
            bTestSecondAttempt = Boolean.TRUE;
        }
        // Need to manage the anchor
        String anchor = request.getParameter( PARAMETER_ANCHOR );
        if ( StringUtils.isNotEmpty( anchor ) )
        {
            LinkedHashMap<String, String> additionalParameters = new LinkedHashMap<>( );
            additionalParameters.put( PARAMETER_ID_FORM, strIdForm );
            additionalParameters.put( PARAMETER_STARTING_DATE_TIME, request.getParameter( PARAMETER_STARTING_DATE_TIME ) );
            additionalParameters.put( PARAMETER_NB_PLACE_TO_TAKE, Integer.toString( _nNbPlacesToTake ) );
            additionalParameters.put( PARAMETER_ANCHOR, MARK_ANCHOR + anchor );
            return redirect( request, VIEW_APPOINTMENT_FORM, additionalParameters );

        }

        String isModification = request.getParameter( PARAMETER_IS_MODIFICATION );
        boolean bModificationForm = false;
        List<Slot> listSlot = null;
        if ( isModification != null )

        {
            bModificationForm = true;

        }
        else
        {

            int nNbConsecutiveSlot = ( _nNbPlacesToTake == 0 ) ? 1 : _nNbPlacesToTake;
            LocalDateTime startingDateTime = LocalDateTime.parse( request.getParameter( PARAMETER_STARTING_DATE_TIME ) );
            if ( !isAuthorizedDate( startingDateTime, getLocale( request ) ) )
            {

                addError( ERROR_MESSAGE_SLOT_FULL, getLocale( request ) );
                return redirect( request, VIEW_APPOINTMENT_CALENDAR, PARAMETER_ID_FORM, nIdForm, PARAMETER_NB_PLACE_TO_TAKE, _nNbPlacesToTake );
            }
            // Get all the week definitions
            List<WeekDefinition> listWeekDefinition = WeekDefinitionService.findListWeekDefinition( nIdForm );
            Map<WeekDefinition, ReservationRule> mapReservationRule = ReservationRuleService.findAllReservationRule( nIdForm, listWeekDefinition );

            listSlot = SlotService.buildListSlot( nIdForm, mapReservationRule, startingDateTime.toLocalDate( ), startingDateTime.toLocalDate( ) );
            listSlot = listSlot.stream( ).filter(
                    s -> ( ( startingDateTime.compareTo( s.getStartingDateTime( ) ) <= 0 ) && ( s.getNbRemainingPlaces( ) > 0 ) && ( s.getIsOpen( ) ) ) )
                    .limit( nNbConsecutiveSlot ).collect( Collectors.toList( ) );

            if ( listSlot == null || listSlot.stream( ).noneMatch( slot -> slot.getStartingDateTime( ).isEqual( startingDateTime ) )
                    || ( _nNbPlacesToTake > 0 && listSlot.size( ) != _nNbPlacesToTake ) || !AppointmentUtilities.isConsecutiveSlots( listSlot ) )
            {
                addError( ERROR_MESSAGE_SLOT_FULL, getLocale( request ) );
                return redirect( request, VIEW_APPOINTMENT_CALENDAR, PARAMETER_ID_FORM, nIdForm, PARAMETER_NB_PLACE_TO_TAKE, _nNbPlacesToTake );
            }

        }

        if ( _notValidatedAppointment == null || _notValidatedAppointment.getIdForm( ) != _appointmentForm.getIdForm( ) )
        {
            if ( _validatedAppointment != null && _validatedAppointment.getIdForm( ) == _appointmentForm.getIdForm( ) )
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
        if ( !bModificationForm )
        {

            boolean bool = true;
            _notValidatedAppointment.setIdForm( nIdForm );
            _notValidatedAppointment.setSlot( null );
            _notValidatedAppointment.setNbMaxPotentialBookedSeats( 0 );
            for ( Slot slot : listSlot )
            {

                // If nIdSlot == 0, the slot has not been created yet
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
                if ( !bTestSecondAttempt && slot.getNbPotentialRemainingPlaces( ) == 0 )
                {
                    _notValidatedAppointment = null;
                    addError( ERROR_MESSAGE_SLOT_FULL, getLocale( request ) );
                    return redirect( request, VIEW_APPOINTMENT_CALENDAR, PARAMETER_ID_FORM, nIdForm, PARAMETER_NB_PLACE_TO_TAKE, _nNbPlacesToTake );
                }

                _notValidatedAppointment.addSlot( slot );

                if ( bool )
                {
                    _notValidatedAppointment.setDateOfTheAppointment( slot.getDate( ).format( Utilities.getFormatter( ) ) );
                    if ( SecurityService.getInstance( ).getRegisteredUser( request ) != null )
                    {
                        setUserInfo( request, _notValidatedAppointment );
                    }
                    FormService.fillAppointmentFormWithReservationRulePart( _appointmentForm,
                            ReservationRuleService.findReservationRuleByIdFormAndClosestToDateOfApply( nIdForm, slot.getDate( ) ) );
                    bool = false;
                }
                AppointmentUtilities.putTimerInSession( request, slot.getIdSlot( ), _notValidatedAppointment, _appointmentForm.getMaxPeoplePerAppointment( ) );
            }
            if ( _notValidatedAppointment.getNbMaxPotentialBookedSeats( ) == 0 )
            {
                addError( ERROR_MESSAGE_SLOT_FULL, getLocale( request ) );
                return redirect( request, VIEW_APPOINTMENT_CALENDAR, PARAMETER_ID_FORM, nIdForm, PARAMETER_NB_PLACE_TO_TAKE, _nNbPlacesToTake );
            }
        }
        else
        {
            // Modification of the Form only, Need to redirect for the anchor
            if ( StringUtils.isEmpty( request.getParameter( PARAMETER_MODIFICATION_FORM ) ) )
            {
                LinkedHashMap<String, String> additionalParameters = new LinkedHashMap<>( );
                additionalParameters.put( PARAMETER_ID_FORM, strIdForm );
                additionalParameters.put( PARAMETER_MODIFICATION_FORM, String.valueOf( Boolean.TRUE ) );
                additionalParameters.put( PARAMETER_IS_MODIFICATION, String.valueOf( Boolean.TRUE ) );
                additionalParameters.put( PARAMETER_ANCHOR, MARK_ANCHOR + STEP_3 );
                return redirect( request, VIEW_APPOINTMENT_FORM, additionalParameters );
            }
        }
        Map<String, Object> model = getModel( );
        Locale locale = getLocale( request );
        StringBuilder strBuffer = new StringBuilder( );
        List<Entry> listEntryFirstLevel = EntryService.getFilter( _appointmentForm.getIdForm( ), true );
        for ( Entry entry : listEntryFirstLevel )
        {
            EntryService.getHtmlEntry( model, entry.getIdEntry( ), strBuffer, locale, true, _notValidatedAppointment );
        }
        FormMessage formMessages = FormMessageService.findFormMessageByIdForm( nIdForm );

        if ( _nNbPlacesToTake != 0 )
        {

            _notValidatedAppointment.setNbBookedSeats( _nNbPlacesToTake );
        }
        model.put( MARK_APPOINTMENT, _notValidatedAppointment );
        model.put( MARK_NBPLACESTOTAKE, _nNbPlacesToTake );
        model.put( PARAMETER_DATE_OF_DISPLAY, _notValidatedAppointment.getSlot( ).get( 0 ).getDate( ) );
        model.put( MARK_FORM, _appointmentForm );
        model.put( MARK_FORM_MESSAGES, formMessages );
        model.put( MARK_STR_ENTRY, strBuffer.toString( ) );
        model.put( MARK_LOCALE, locale );
        model.put( MARK_PLACES, _notValidatedAppointment.getNbMaxPotentialBookedSeats( ) );
        model.put( MARK_LIST_ERRORS, AppointmentDTO.getAllErrors( locale ) );
        LuteceUser user = SecurityService.getInstance( ).getRegisteredUser( request );
        if( user != null )
        {
        	model.put( MARK_USER,  user );
        	model.put( MARK_USER_PREFERRED_NAME, user.getUserInfo( AppPropertiesService.getProperty( PROPERTY_USER_ATTRIBUTE_PREFERED_NAME, StringUtils.EMPTY ) ) );
        }
        else
        {
        	model.put( MARK_USER,  null );
        }

        // Check the Access Controls of the form
        XPage accessControlPage = AccessControlService.getInstance( ).doExecuteAccessControl(
        		request,
        		nIdForm,
        		Form.RESOURCE_TYPE,
        		null
        		);
        if ( accessControlPage != null )
        {
        	return accessControlPage;
        }

        model.put( SecurityTokenService.MARK_TOKEN, SecurityTokenService.getInstance( ).getToken( request, ACTION_DO_VALIDATE_FORM ) );
        HtmlTemplate templateForm = AppTemplateService.getTemplate( TEMPLATE_HTML_CODE_FORM, locale, model );
        model.put( MARK_FORM_HTML, templateForm.getHtml( ) );
        XPage xPage = getXPage( TEMPLATE_APPOINTMENT_FORM, locale, model );

        if ( _appointmentForm.getDisplayTitleFo( ) )
        {
            xPage.setTitle( _appointmentForm.getTitle( ) );
        }
        return xPage;
    }

    /**
     * Do validate data entered by a user to fill a form
     *
     * @param request
     *            The request
     * @return The next URL to redirect to
     * @throws SiteMessageException
     * @throws UserNotSignedException
     * @throws AccessDeniedException
     */
    @Action( ACTION_DO_VALIDATE_FORM )
    public synchronized XPage doValidateForm( HttpServletRequest request ) throws UserNotSignedException, AccessDeniedException
    {
        // CSRF Token control
        if ( !SecurityTokenService.getInstance( ).validate( request, ACTION_DO_VALIDATE_FORM ) )
        {
            throw new AccessDeniedException( MESSAGE_ERROR_TOKEN );
        }
        checkMyLuteceAuthentication( _appointmentForm, request );
        String strIdForm = request.getParameter( PARAMETER_ID_FORM );
        if ( _notValidatedAppointment == null || _appointmentForm == null || _notValidatedAppointment.getIdForm( ) != _appointmentForm.getIdForm( ) )
        {

            addError( ERROR_MESSAGE_FORM_NO_MORE_VALID, getLocale( request ) );
            _notValidatedAppointment = null;
            _validatedAppointment = null;
            return redirectView( request, VIEW_APPOINTMENT_FORM_LIST );
        }
        List<GenericAttributeError> listFormErrors = new ArrayList<>( );
        Locale locale = request.getLocale( );
        String strEmail = request.getParameter( PARAMETER_EMAIL );
        String strEmailConfirm = request.getParameter( PARAMETER_EMAIL_CONFIRMATION );
        String strFirstName = request.getParameter( PARAMETER_FIRST_NAME );
        String strLastName = request.getParameter( PARAMETER_LAST_NAME );
        AppointmentUtilities.checkDateOfTheAppointmentIsNotBeforeNow( _notValidatedAppointment, locale, listFormErrors );
        AppointmentUtilities.checkEmail( strEmail, strEmailConfirm, _appointmentForm, locale, listFormErrors );
        int nbBookedSeats = _nNbPlacesToTake;
        if ( _nNbPlacesToTake == 0 )
        {

            nbBookedSeats = AppointmentUtilities.checkAndReturnNbBookedSeats( request.getParameter( PARAMETER_NUMBER_OF_BOOKED_SEATS ), _appointmentForm,
                    _notValidatedAppointment, locale, listFormErrors );

        }
        AppointmentUtilities.fillAppointmentDTO( _notValidatedAppointment, nbBookedSeats, strEmail, strEmailConfirm, strFirstName, strLastName );
        AppointmentUtilities.validateFormAndEntries( _notValidatedAppointment, request, listFormErrors, false );
        AppointmentUtilities.fillInListResponseWithMapResponse( _notValidatedAppointment );
        AppointmentUtilities.setAppointmentPhoneNumberValuesFromResponse( _notValidatedAppointment );

        boolean bErrors = false;
        if ( _appointmentForm.getEnableMandatoryEmail( )
                && !AppointmentUtilities.checkNbDaysBetweenTwoAppointmentsTaken( _notValidatedAppointment, strEmail, _appointmentForm ) )
        {
            addError( ERROR_MESSAGE_NB_MIN_DAYS_BETWEEN_TWO_APPOINTMENTS, locale );
            bErrors = true;
        }
        if ( _appointmentForm.getEnableMandatoryEmail( )
                && !AppointmentUtilities.checkNbMaxAppointmentsOnAGivenPeriod( _notValidatedAppointment, strEmail, _appointmentForm ) )
        {
            addError( ERROR_MESSAGE_NB_MAX_APPOINTMENTS_ON_A_PERIOD, locale );
            bErrors = true;
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
            addError( strErrorMessageDateWithAppointments );
            bErrors = true;
        }
        if ( CollectionUtils.isNotEmpty( listFormErrors ) )
        {
            getModel( ).put( MARK_FORM_ERRORS, listFormErrors );
            for ( GenericAttributeError error : listFormErrors )
            {
            	addError( error.getErrorMessage( ) );
            }
            bErrors = true;
        }
        if ( bErrors )
        {
            LinkedHashMap<String, String> additionalParameters = new LinkedHashMap<>( );
            additionalParameters.put( PARAMETER_ID_FORM, strIdForm );
            additionalParameters.put( PARAMETER_MODIFICATION_FORM, String.valueOf( Boolean.TRUE ) );
            additionalParameters.put( PARAMETER_IS_MODIFICATION, String.valueOf( Boolean.TRUE ) );
            additionalParameters.put( PARAMETER_ANCHOR, MARK_ANCHOR + STEP_3 );
            return redirect( request, VIEW_APPOINTMENT_FORM, additionalParameters );
        }
        _validatedAppointment = _notValidatedAppointment;
        _notValidatedAppointment = null;
        String anchor = request.getParameter( PARAMETER_ANCHOR );
        if ( StringUtils.isNotEmpty( anchor ) )
        {
            Map<String, String> additionalParameters = new HashMap<>( );
            additionalParameters.put( PARAMETER_ANCHOR, MARK_ANCHOR + anchor );

            return redirect( request, VIEW_DISPLAY_RECAP_APPOINTMENT, additionalParameters );
        }
        else
        {
            return redirectView( request, VIEW_DISPLAY_RECAP_APPOINTMENT );
        }
    }

    /**
     * Display the recap before validating an appointment
     *
     * @param request
     *            The request
     * @return The HTML content to display or the next URL to redirect to
     * @throws UserNotSignedException
     * @throws AccessDeniedException
     */
    @View( VIEW_DISPLAY_RECAP_APPOINTMENT )
    public synchronized XPage displayRecapAppointment( HttpServletRequest request ) throws UserNotSignedException, AccessDeniedException
    {
        checkMyLuteceAuthentication( _appointmentForm, request );
        String anchor = request.getParameter( PARAMETER_ANCHOR );
        String strModifDateAppointment = request.getParameter( PARAMETER_MODIF_DATE );

        if ( StringUtils.isNotEmpty( anchor ) )
        {
            Map<String, String> additionalParameters = new HashMap<>( );
            additionalParameters.put( PARAMETER_ANCHOR, MARK_ANCHOR + anchor );
            return redirect( request, VIEW_DISPLAY_RECAP_APPOINTMENT, additionalParameters );
        }

        if ( _validatedAppointment == null || _appointmentForm == null || _validatedAppointment.getIdForm( ) != _appointmentForm.getIdForm( ) )
        {
            addError( ERROR_MESSAGE_FORM_NO_MORE_VALID, getLocale( request ) );
            _notValidatedAppointment = null;
            _validatedAppointment = null;
            return redirectView( request, VIEW_APPOINTMENT_FORM_LIST );
        }
        Map<String, Object> model = new HashMap<>( );
        if ( _appointmentForm.getEnableCaptcha( ) && getCaptchaService( ).isAvailable( ) )
        {
            model.put( MARK_CAPTCHA, getCaptchaService( ).getHtmlCode( ) );
        }
        if ( strModifDateAppointment != null && Boolean.parseBoolean( strModifDateAppointment ) )
        {
            model.put( MARK_MODIFICATION_DATE_APPOINTMENT, Boolean.TRUE );

        }
        else
        {

            model.put( MARK_MODIFICATION_DATE_APPOINTMENT, Boolean.FALSE );

        }
        model.put( MARK_FORM_MESSAGES, FormMessageService.findFormMessageByIdForm( _validatedAppointment.getIdForm( ) ) );
        fillCommons( model );
        model.put( MARK_APPOINTMENT, _validatedAppointment );
        Locale locale = getLocale( request );
        model.put( MARK_LIST_RESPONSE_RECAP_DTO, AppointmentUtilities.buildListResponse( _validatedAppointment, request, locale ) );
        model.put( MARK_FORM, _appointmentForm );
        model.put( MARK_NBPLACESTOTAKE, _nNbPlacesToTake );
        model.put( PARAMETER_DATE_OF_DISPLAY, _validatedAppointment.getSlot( ).get( 0 ).getDate( ) );
        model.put( SecurityTokenService.MARK_TOKEN, SecurityTokenService.getInstance( ).getToken( request, ACTION_DO_MAKE_APPOINTMENT ) );

        return getXPage( TEMPLATE_APPOINTMENT_FORM_RECAP, locale, model );
    }

    /**
     * Do save an appointment into the database if it is valid
     *
     * @param request
     *            The request
     * @return The XPage to display
     * @throws UserNotSignedException
     * @throws AccessDeniedException
     */
    @Action( ACTION_DO_MAKE_APPOINTMENT )
    public synchronized XPage doMakeAppointment( HttpServletRequest request ) throws UserNotSignedException, AccessDeniedException
    {
        checkMyLuteceAuthentication( _appointmentForm, request );
        // CSRF Token control
        if ( !SecurityTokenService.getInstance( ).validate( request, ACTION_DO_MAKE_APPOINTMENT ) )
        {
            throw new AccessDeniedException( MESSAGE_ERROR_TOKEN );
        }
        if ( _validatedAppointment == null || _appointmentForm == null || _validatedAppointment.getIdForm( ) != _appointmentForm.getIdForm( ) )
        {
            addError( ERROR_MESSAGE_FORM_NO_MORE_VALID, getLocale( request ) );
            _notValidatedAppointment = null;
            _validatedAppointment = null;
            return redirectView( request, VIEW_APPOINTMENT_FORM_LIST );
        }
        if ( !_appointmentForm.getIsActive( ) )
        {
            addError( ERROR_MESSAGE_FORM_NOT_ACTIVE, getLocale( request ) );
            return redirect( request, VIEW_APPOINTMENT_CALENDAR, PARAMETER_ID_FORM, _appointmentForm.getIdForm( ), PARAMETER_NB_PLACE_TO_TAKE,
                    _nNbPlacesToTake );
        }

        if ( StringUtils.isNotEmpty( request.getParameter( PARAMETER_BACK ) ) )
        {
            LinkedHashMap<String, String> parameters = new LinkedHashMap<>( );
            parameters.put( PARAMETER_ID_FORM, String.valueOf( _validatedAppointment.getIdForm( ) ) );
            parameters.put( PARAMETER_IS_MODIFICATION, String.valueOf( Boolean.TRUE ) );

            return redirect( request, VIEW_APPOINTMENT_FORM, parameters );
        }
        if ( _appointmentForm.getEnableCaptcha( ) && getCaptchaService( ).isAvailable( ) && !getCaptchaService( ).validate( request ) )
        {
            addError( ERROR_MESSAGE_CAPTCHA, getLocale( request ) );
            return redirect( request, VIEW_DISPLAY_RECAP_APPOINTMENT, PARAMETER_ID_FORM, _validatedAppointment.getIdForm( ) );
        }

        int nIdAppointment;
        try
        {

            nIdAppointment = SlotSafeService.saveAppointment( _validatedAppointment, request );

        }
        catch( SlotFullException e )
        {

            addError( ERROR_MESSAGE_SLOT_FULL, getLocale( request ) );
            return redirect( request, VIEW_APPOINTMENT_CALENDAR, PARAMETER_ID_FORM, _validatedAppointment.getIdForm( ), PARAMETER_NB_PLACE_TO_TAKE,
                    _nNbPlacesToTake );
        }
        catch( SlotEditTaskExpiredTimeException e )
        {
            addError( ERROR_MESSAGE_SLOT_EDIT_TASK_EXPIRED_TIME, getLocale( request ) );
            return redirect( request, VIEW_APPOINTMENT_CALENDAR, PARAMETER_ID_FORM, _validatedAppointment.getIdForm( ), PARAMETER_NB_PLACE_TO_TAKE,
                    _nNbPlacesToTake );
        }
        catch( AppointmentSavedException e )
        {
            nIdAppointment = _validatedAppointment.getIdAppointment( );
            AppLogService.error( "Error Save appointment: " + e.getMessage( ), e );
        }
        AppLogService.info( LogUtilities.buildLog( ACTION_DO_MAKE_APPOINTMENT, Integer.toString( nIdAppointment ), null ) );
        AppointmentAsynchronousUploadHandler.getHandler( ).removeSessionFiles( request.getSession( ) );
        _nNbPlacesToTake = 0;
        int nIdForm = _validatedAppointment.getIdForm( );
       // _validatedAppointment = null;

        // Remove the session data of this form
        AccessControlService.getInstance( ).cleanSessionData( request, nIdForm, Form.RESOURCE_TYPE );

        String anchor = request.getParameter( PARAMETER_ANCHOR );
        if ( StringUtils.isNotEmpty( anchor ) )
        {
            LinkedHashMap<String, String> additionalParameters = new LinkedHashMap<>( );
            additionalParameters.put( PARAMETER_ID_FORM, String.valueOf( nIdForm ) );
            additionalParameters.put( PARAMETER_ANCHOR, MARK_ANCHOR + anchor );
            return redirect( request, VIEW_GET_APPOINTMENT_CREATED, additionalParameters );
        }
        else
        {
            return redirect( request, VIEW_GET_APPOINTMENT_CREATED, PARAMETER_ID_FORM, nIdForm );
        }
    }

    /**
     * Get the page to notify the user that the appointment has been created
     *
     * @param request
     *            The request
     * @return The XPage to display
     */
    @View( VIEW_GET_APPOINTMENT_CREATED )
    public synchronized XPage getAppointmentCreated( HttpServletRequest request )
    {
    	if( _validatedAppointment == null )
    	{
    		return redirectView( request, VIEW_APPOINTMENT_FORM_LIST );
    	}
        int nIdForm = Integer.parseInt( request.getParameter( PARAMETER_ID_FORM ) );
        FormMessage formMessages = FormMessageService.findFormMessageByIdForm( nIdForm );
        AppointmentFormDTO form = FormService.buildAppointmentForm( nIdForm, 0 );
        Slot firstSlot = _validatedAppointment.getSlot().get(0);
        Slot lastSlot = firstSlot;
        if (  _validatedAppointment.getSlot().size( ) > 1 )
        {
                    lastSlot = _validatedAppointment.getSlot().get(_validatedAppointment.getSlot().size( ) -1 );
        }
        String strTimeBegin = firstSlot.getStartingDateTime( ).toLocalTime( ).toString( );
        String strTimeEnd = lastSlot.getEndingDateTime( ).toLocalTime( ).toString( );
        formMessages.setTextAppointmentCreated( formMessages.getTextAppointmentCreated( ).replace( MARK_REF, _validatedAppointment.getReference( ) )
                .replace( MARK_DATE_APP, firstSlot.getStartingDateTime().format( Utilities.getFormatter( ) ) )
                .replace( MARK_TIME_BEGIN, strTimeBegin ).replace( MARK_TIME_END, strTimeEnd) );
        Map<String, Object> model = new HashMap<>( );
        model.put( MARK_LIST_RESPONSE_RECAP_DTO, AppointmentUtilities.buildListResponse( _validatedAppointment, request, getLocale( request ) ) );
        model.put( MARK_DATE_APPOINTMENT, firstSlot.getDate().format( Utilities.getFormatter( ) ) );
        model.put( MARK_STARTING_TIME_APPOINTMENT, firstSlot.getStartingTime( ) );
        model.put( MARK_ENDING_TIME_APPOINTMENT, lastSlot.getEndingTime( ) );
        model.put( MARK_USER,  _validatedAppointment.getUser( ) );
        model.put( MARK_PLACES, _validatedAppointment.getNbPlaces( ) );
        model.put( MARK_FORM, form );
        model.put( MARK_FORM_MESSAGES, formMessages );
        _appointmentForm = null;
        _validatedAppointment = null;
        return getXPage( TEMPLATE_APPOINTMENT_CREATED, getLocale( request ), model );
    }

    /**
     * Return to the display recap view with the new date selected on the calendar
     *
     * @param request
     *            the request
     * @return to the display recap view
     * @throws AccessDeniedException
     * @throws SiteMessageException
     */

    @View( VIEW_CHANGE_DATE_APPOINTMENT )
    public synchronized XPage getViewChangeDateAppointment( HttpServletRequest request )
    {
        String strIdForm = request.getParameter( PARAMETER_ID_FORM );
        LocalDateTime startingDateTime = LocalDateTime.parse( request.getParameter( PARAMETER_STARTING_DATE_TIME ) );
        Locale locale = getLocale( request );
        int nIdForm = Integer.parseInt( strIdForm );
        Form form = FormService.findFormLightByPrimaryKey( nIdForm );

        if ( !form.getIsActive( ) || _validatedAppointment.getStartingDateTime( ).isBefore( LocalDateTime.now( ) )
                || !isAuthorizedDate( startingDateTime, locale ) )
        {
            _validatedAppointment = null;
            addError( ERROR_MESSAGE_FORM_NOT_ACTIVE, getLocale( request ) );
            return redirect( request, VIEW_APPOINTMENT_CALENDAR, PARAMETER_ID_FORM, form.getIdForm( ), PARAMETER_NB_PLACE_TO_TAKE, _nNbPlacesToTake );
        }
        int nNbConsecutiveSlot = ( _nNbPlacesToTake == 0 ) ? 1 : _nNbPlacesToTake;
        List<WeekDefinition> listWeekDefinition = WeekDefinitionService.findListWeekDefinition( nIdForm );
        Map<WeekDefinition, ReservationRule> mapReservationRule = ReservationRuleService.findAllReservationRule( nIdForm, listWeekDefinition );
        List<Slot> listSlot = SlotService.buildListSlot( nIdForm, mapReservationRule, startingDateTime.toLocalDate( ), startingDateTime.toLocalDate( ) );
        listSlot = listSlot.stream( )
                .filter( s -> ( ( startingDateTime.compareTo( s.getStartingDateTime( ) ) <= 0 ) && ( s.getNbRemainingPlaces( ) > 0 ) && ( s.getIsOpen( ) ) ) )
                .limit( nNbConsecutiveSlot ).collect( Collectors.toList( ) );

        if ( listSlot == null || ( _nNbPlacesToTake > 0 && listSlot.size( ) != _nNbPlacesToTake ) || !AppointmentUtilities.isConsecutiveSlots( listSlot ) )
        {
            addError( ERROR_MESSAGE_SLOT_FULL, getLocale( request ) );
            return redirect( request, VIEW_APPOINTMENT_CALENDAR, PARAMETER_ID_FORM, nIdForm, PARAMETER_NB_PLACE_TO_TAKE, _nNbPlacesToTake );
        }

        boolean bool = true;
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

            // Need to check competitive access
            // May be the slot is already taken at the same time
            if ( slot.getNbPotentialRemainingPlaces( ) <= 0 )
            {
                addError( ERROR_MESSAGE_SLOT_FULL, locale );
                return redirect( request, VIEW_APPOINTMENT_CALENDAR, PARAMETER_ID_FORM, nIdForm, PARAMETER_NB_PLACE_TO_TAKE, _nNbPlacesToTake );
            }

            _validatedAppointment.addSlot( slot );

            if ( bool )
            {
                _validatedAppointment.setDateOfTheAppointment( slot.getDate( ).format( Utilities.getFormatter( ) ) );
                ReservationRule reservationRule = ReservationRuleService.findReservationRuleByIdFormAndClosestToDateOfApply( nIdForm, slot.getDate( ) );
                _appointmentForm = FormService.buildAppointmentForm( nIdForm, reservationRule );
                bool = false;
            }
            AppointmentUtilities.putTimerInSession( request, slot.getIdSlot( ), _validatedAppointment, _appointmentForm.getMaxPeoplePerAppointment( ) );
        }

        if ( _validatedAppointment.getNbMaxPotentialBookedSeats( ) == 0 )
        {
            addError( ERROR_MESSAGE_SLOT_FULL, locale );
            return redirect( request, VIEW_APPOINTMENT_CALENDAR, PARAMETER_ID_FORM, nIdForm, PARAMETER_NB_PLACE_TO_TAKE, _nNbPlacesToTake );
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
        additionalParameters.put( PARAMETER_MODIF_DATE, "true" );
        additionalParameters.put( PARAMETER_ID_FORM, Integer.toString( nIdForm ) );
        return redirect( request, VIEW_DISPLAY_RECAP_APPOINTMENT, additionalParameters );
    }

    /**
     * Get the view of all the forms on front office side
     *
     * @param request
     *            the request
     * @return the xpage
     */
    @View( value = VIEW_APPOINTMENT_FORM_LIST, defaultView = true )
    public synchronized XPage getFormList( HttpServletRequest request )
    {
        Locale locale = getLocale( request );
        _appointmentForm = null;
        _validatedAppointment = null;
        String strHtmlContent = getFormListHtml( locale, getModel( ) );

        XPage xPage = getXPage( );
        xPage.setContent( strHtmlContent );
        return xPage;
    }

    /**
     * Get the view for the user who wants to cancel its appointment
     *
     * @param request
     * @return the view
     */
    @View( VIEW_GET_VIEW_CANCEL_APPOINTMENT )
    public synchronized XPage getViewCancelAppointment( HttpServletRequest request )
    {
        String refAppointment = request.getParameter( PARAMETER_REF_APPOINTMENT );
        Appointment appointment = null;
        if ( StringUtils.isNotEmpty( refAppointment ) )
        {
            appointment = AppointmentService.findAppointmentByReference( refAppointment );
        }
        Map<String, Object> model = new HashMap<>( );
        model.put( PARAMETER_REF_APPOINTMENT, refAppointment );
        model.put( SecurityTokenService.MARK_TOKEN, SecurityTokenService.getInstance( ).getToken( request, ACTION_DO_CANCEL_APPOINTMENT ) );
        if ( appointment != null )
        {

            if ( appointment.getIsCancelled( ) )
            {
                model.put( MARK_APPOINTMENT_ALREADY_CANCELLED, Boolean.TRUE );
            }

            int nIdAppointment = appointment.getIdAppointment( );
            AppointmentDTO appointmentDto = AppointmentService.buildAppointmentDTOFromIdAppointment( nIdAppointment );
            // Check if the appointment is passed
            if ( appointmentDto.getStartingDateTime( ).isBefore( LocalDateTime.now( ) ) )
            {
                model.put( MARK_APPOINTMENT_PASSED, Boolean.TRUE );
            }
            model.put( MARK_DATE_APPOINTMENT, appointmentDto.getDateOfTheAppointment( ) );
            model.put( MARK_STARTING_TIME_APPOINTMENT, appointmentDto.getStartingTime( ) );
            model.put( MARK_ENDING_TIME_APPOINTMENT, appointmentDto.getEndingTime( ) );
            model.put( MARK_PLACES, appointment.getNbPlaces( ) );
            model.put( MARK_FORM, FormService.buildAppointmentForm( appointmentDto.getIdForm( ), 0 ) );
            model.put( MARK_FORM_MESSAGES, FormMessageService.findFormMessageByIdForm( appointmentDto.getIdForm( ) ) );
            AppointmentDTO appointmentDTO = AppointmentService.buildAppointmentDTOFromIdAppointment( nIdAppointment );
            appointmentDTO.setListResponse( AppointmentResponseService.findAndBuildListResponse( nIdAppointment, request ) );
            appointmentDTO.setMapResponsesByIdEntry( AppointmentResponseService.buildMapFromListResponse( appointmentDTO.getListResponse( ) ) );
            model.put( MARK_LIST_RESPONSE_RECAP_DTO, AppointmentUtilities.buildListResponse( appointmentDTO, request, getLocale( request ) ) );
            model.put( MARK_USER, UserService.findUserById( appointment.getIdUser( ) ) );

        }
        else
        {
            model.put( MARK_NO_APPOINTMENT_WITH_THIS_REFERENCE, Boolean.TRUE );
        }
        Locale locale = getLocale( request );
        XPage xpage = getXPage( TEMPLATE_CANCEL_APPOINTMENT, locale, model );
        xpage.setTitle( I18nService.getLocalizedString( MESSAGE_CANCEL_APPOINTMENT_PAGE_TITLE, locale ) );
        return xpage;

    }

    /**
     * Cancel an appointment
     *
     * @param request
     * @return the confirmation view of the appointment cancelled
     */
    @Action( ACTION_DO_CANCEL_APPOINTMENT )
    public synchronized XPage doCancelAppointment( HttpServletRequest request ) throws AccessDeniedException
    {
        // CSRF Token control
        if ( !SecurityTokenService.getInstance( ).validate( request, ACTION_DO_CANCEL_APPOINTMENT ) )
        {
            throw new AccessDeniedException( MESSAGE_ERROR_TOKEN );
        }

        String strRef = request.getParameter( PARAMETER_REF_APPOINTMENT );
        if ( StringUtils.isNotEmpty( strRef ) )
        {
            Appointment appointment = AppointmentService.findAppointmentByReference( strRef );
            AppointmentDTO appointmentDto = AppointmentService.buildAppointmentDTOFromIdAppointment( appointment.getIdAppointment( ) );

            // Accept only one cancel !!!
            if ( !appointment.getIsCancelled( ) && !appointmentDto.getStartingDateTime( ).isBefore( LocalDateTime.now( ) ) )
            {
                // Check if the appointment is passed
                if ( appointment.getIdActionCancelled( ) > 0 )
                {
                    try
                    {
                        WorkflowService.getInstance( ).doProcessAction( appointment.getIdAppointment( ), Appointment.APPOINTMENT_RESOURCE_TYPE,
                                appointment.getIdActionCancelled( ), appointmentDto.getIdForm( ), request, request.getLocale( ), true, null );
                        AppointmentListenerManager.notifyAppointmentWFActionTriggered( appointment.getIdAppointment( ), appointment.getIdActionCancelled( ) );
                    }
                    catch( Exception e )
                    {
                        AppLogService.error( "Error Workflow", e );
                    }
                }
                else
                {
                    appointment.setIsCancelled( Boolean.TRUE );
                    AppointmentService.updateAppointment( appointment );
                    AppLogService.info( LogUtilities.buildLog( ACTION_DO_CANCEL_APPOINTMENT, Integer.toString( appointment.getIdAppointment( ) ), null ) );
                }
                Map<String, String> mapParameters = new HashMap<>( );
                if ( StringUtils.isNotEmpty( request.getParameter( PARAMETER_FROM_MY_APPOINTMENTS ) ) )
                {
                    String strReferer = request.getHeader( PARAMETER_REFERER );
                    if ( StringUtils.isNotEmpty( strReferer ) )
                    {
                        mapParameters.put( MARK_FROM_URL, strReferer );
                    }
                    mapParameters.put( PARAMETER_FROM_MY_APPOINTMENTS, request.getParameter( PARAMETER_FROM_MY_APPOINTMENTS ) );
                }
                mapParameters.put( PARAMETER_ID_FORM, Integer.toString( appointmentDto.getIdForm( ) ) );
                return redirect( request, VIEW_APPOINTMENT_CANCELED, mapParameters );
            }
        }
        return redirectView( request, VIEW_APPOINTMENT_FORM_LIST );
    }

    /**
     * Get the page to confirm that the appointment has been canceled
     *
     * @param request
     *            The request
     * @return The XPage to display
     */
    @View( VIEW_APPOINTMENT_CANCELED )
    public synchronized XPage getAppointmentCanceled( HttpServletRequest request )
    {
        String strIdForm = request.getParameter( PARAMETER_ID_FORM );
        if ( StringUtils.isNotEmpty( strIdForm ) && StringUtils.isNumeric( strIdForm ) )
        {
            int nIdForm = Integer.parseInt( strIdForm );
            Map<String, Object> model = new HashMap<>( );
            model.put( MARK_FORM_MESSAGES, FormMessageService.findFormMessageByIdForm( nIdForm ) );
            if ( Boolean.parseBoolean( request.getParameter( PARAMETER_FROM_MY_APPOINTMENTS ) ) )
            {
                String strFromUrl = request.getParameter( MARK_FROM_URL );
                model.put( MARK_BACK_URL, StringUtils.isNotEmpty( strFromUrl ) ? strFromUrl : getViewUrl( VIEW_GET_MY_APPOINTMENTS ) );
            }
            return getXPage( TEMPLATE_APPOINTMENT_CANCELED, getLocale( request ), model );
        }
        return redirectView( request, VIEW_APPOINTMENT_FORM_LIST );
    }

    /**
     * Get the page to view the appointments of a user
     *
     * @param request
     *            The request
     * @return The XPage to display
     * @throws UserNotSignedException
     *             If the authentication is enabled and the user has not signed in
     */
    @View( VIEW_GET_MY_APPOINTMENTS )
    public synchronized XPage getMyAppointments( HttpServletRequest request ) throws UserNotSignedException
    {
        if ( !SecurityService.isAuthenticationEnable( ) )
        {
            return redirectView( request, VIEW_APPOINTMENT_FORM_LIST );
        }
        Locale locale = getLocale( request );
        XPage xPage = getXPage( );
        xPage.setContent( getMyAppointmentsHtml( request, locale, getModel( ) ) );
        xPage.setTitle( I18nService.getLocalizedString( MESSAGE_MY_APPOINTMENTS_PAGE_TITLE, locale ) );
        return xPage;
    }

    /**
     * Get the HTML content of the my appointment page of a user
     *
     * @param request
     *            The request
     * @param locale
     *            The locale
     * @return The HTML content, or null if the
     * @throws UserNotSignedException
     *             If the user has not signed in
     * @deprecated use {@link #getMyAppointmentsHtml(HttpServletRequest, Locale, Map)}
     */
    @Deprecated
    public static String getMyAppointmentsXPage( HttpServletRequest request, Locale locale, Map<String, Object> model ) throws UserNotSignedException
    {
        if ( !SecurityService.isAuthenticationEnable( ) )
        {
            return null;
        }
        LuteceUser luteceUser = SecurityService.getInstance( ).getRegisteredUser( request );
        if ( luteceUser == null )
        {
            throw new UserNotSignedException( );
        }
        AppointmentFilterDTO appointmentFilter = new AppointmentFilterDTO( );
        appointmentFilter.setGuid( luteceUser.getName( ) );
        List<AppointmentDTO> listAppointmentDTO = AppointmentService.findListAppointmentsDTOByFilter( appointmentFilter );
        for ( AppointmentDTO apptDto : listAppointmentDTO )
        {
            Form form = FormService.findFormLightByPrimaryKey( apptDto.getIdForm( ) );
            if ( form.getIdWorkflow( ) > 0 && WorkflowService.getInstance( ).isAvailable( ) )
            {

                apptDto.setListWorkflowActions( WorkflowService.getInstance( ).getActions( apptDto.getIdAppointment( ), Appointment.APPOINTMENT_RESOURCE_TYPE,
                        form.getIdWorkflow( ), luteceUser ) );

            }
        }

        model = ( model == null ) ? new HashMap<>( ) : model;
        model.put( MARK_LIST_APPOINTMENTS, listAppointmentDTO );
        model.put( MARK_FORM_LIST, FormService.findAllInReferenceList( ) );
        model.put( MARK_LOCALE_DATE_TIME, LocalDateTime.now( ) );
        HtmlTemplate template = AppTemplateService.getTemplate( TEMPLATE_MY_APPOINTMENTS, locale, model );
        return template.getHtml( );
    }

    /**
     * Get the HTML content of a user's "My appointments" page
     *
     * @param request
     *            The request
     * @param locale
     *            The locale
     * @return The HTML content, or null if the user is not logged in
     * @throws UserNotSignedException
     *             If the user has not signed in
     */
    public static String getMyAppointmentsHtml( HttpServletRequest request, Locale locale, Map<String, Object> model ) throws UserNotSignedException
    {
        if ( !SecurityService.isAuthenticationEnable( ) )
        {
            return null;
        }
        LuteceUser luteceUser = SecurityService.getInstance( ).getRegisteredUser( request );
        if ( luteceUser == null )
        {
            throw new UserNotSignedException( );
        }
        AppointmentFilterDTO appointmentFilter = new AppointmentFilterDTO( );
        appointmentFilter.setGuid( luteceUser.getName( ) );
        List<AppointmentDTO> listAppointmentDTO = AppointmentService.findListAppointmentsDTOByFilter( appointmentFilter );
        for ( AppointmentDTO apptDto : listAppointmentDTO )
        {
            Form form = FormService.findFormLightByPrimaryKey( apptDto.getIdForm( ) );
            if ( form.getIdWorkflow( ) > 0 && WorkflowService.getInstance( ).isAvailable( ) )
            {
                apptDto.setListWorkflowActions( WorkflowService.getInstance( ).getActions( apptDto.getIdAppointment( ), Appointment.APPOINTMENT_RESOURCE_TYPE,
                        form.getIdWorkflow( ), luteceUser ) );
            }
        }

        model = ( model == null ) ? new HashMap<>( ) : model;
        model.put( MARK_LIST_APPOINTMENTS, listAppointmentDTO );
        model.put( MARK_FORM_LIST, FormService.findAllInReferenceList( ) );
        model.put( MARK_LOCALE_DATE_TIME, LocalDateTime.now( ) );
        HtmlTemplate template = AppTemplateService.getTemplate( TEMPLATE_MY_APPOINTMENTS, locale, model );
        return template.getHtml( );
    }

    /**
     * Get the html content of the list of forms
     *
     * @param locale
     *            The locale
     * @param model
     *            The model
     * @return The HTML content to display
     */
    public static String getFormListHtml( Locale locale, Map<String, Object> model )
    {
        model = ( model == null ) ? new HashMap<>( ) : model;
        List<AppointmentFormDTO> listAppointmentForm = FormService.buildAllActiveAndDisplayedOnPortletAppointmentForm( );
        // We keep only the active
        if ( CollectionUtils.isNotEmpty( listAppointmentForm ) )
        {
            listAppointmentForm = listAppointmentForm.stream( )
                    .filter( a -> ( a.getDateStartValidity( ) != null ) && ( a.getDateStartValidity( ).toLocalDate( ).isBefore( LocalDate.now( ) )
                            || a.getDateStartValidity( ).toLocalDate( ).equals( LocalDate.now( ) ) ) )
                    .sorted( ( a1, a2 ) -> a1.getTitle( ).compareTo( a2.getTitle( ) ) ).collect( Collectors.toList( ) );
        }
        List<String> icons = new ArrayList<>( );
        for ( AppointmentFormDTO form : listAppointmentForm )
        {
            ImageResource img = form.getIcon( );
            if ( img == null || img.getImage( ) == null || StringUtils.isEmpty( img.getMimeType( ) )
                    || StringUtils.equals( img.getMimeType( ), MARK_ICON_NULL ) )
            {
                icons.add( MARK_ICON_NULL );
            }
            else
            {
                byte [ ] imgBytesAsBase64 = Base64.encodeBase64( img.getImage( ) );
                String imgDataAsBase64 = new String( imgBytesAsBase64 );
                String strMimeType = img.getMimeType( );
                String imgAsBase64 = MARK_DATA + MARK_COLON + strMimeType + MARK_SEMI_COLON + MARK_BASE_64 + MARK_COMMA + imgDataAsBase64;
                icons.add( imgAsBase64 );
            }
        }
        model.put( MARK_ICONS, icons );
        model.put( MARK_FORM_LIST, listAppointmentForm );
        HtmlTemplate template = AppTemplateService.getTemplate( TEMPLATE_APPOINTMENT_FORM_LIST, locale, model );
        return template.getHtml( );
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
    public synchronized XPage getWorkflowActionForm( HttpServletRequest request ) throws UserNotSignedException
    {
        String strIdAction = request.getParameter( PARAMETER_ID_ACTION );
        String refAppointment = request.getParameter( PARAMETER_REF_APPOINTMENT );
        if ( StringUtils.isNotEmpty( strIdAction ) && StringUtils.isNumeric( strIdAction ) && StringUtils.isNotEmpty( refAppointment ) )
        {
            int nIdAction = Integer.parseInt( strIdAction );
            if ( WorkflowService.getInstance( ).isDisplayTasksForm( nIdAction, getLocale( request ) ) )
            {
                AppointmentDTO appointment = AppointmentService.buildAppointmentDTOFromRefAppointment( refAppointment );
                // Check if an appointment Object was found and built with the given reference
                if( appointment == null )
                {
                	// When the appointment Object doesn't exist, we display an error message to the user
                	return getXPage( TEMPLATE_ERROR_APPOINTMENT_REFERENCE, getLocale( request ), null );
                }
                ITaskService taskService = SpringContextService.getBean( TaskService.BEAN_SERVICE );
                List<ITask> listActionTasks = taskService.getListTaskByIdAction( nIdAction, getLocale( request ) );
                if ( listActionTasks.stream( ).anyMatch( task -> task.getTaskType( ).getKey( ).equals( "taskReportAppointment" ) ) )
                {
                    Map<String, String> additionalParameters = new HashMap<>( );
                    additionalParameters.put( PARAMETER_REF_APPOINTMENT, appointment.getReference( ) );
                    additionalParameters.put( PARAMETER_NB_PLACE_TO_TAKE, String.valueOf( appointment.getNbBookedSeats( ) ) );
                    additionalParameters.put( PARAMETER_ID_FORM, String.valueOf( appointment.getIdForm( ) ) );
                    return redirect( request, VIEW_APPOINTMENT_CALENDAR, additionalParameters );
                }
                String strHtmlTasksForm = WorkflowService.getInstance( ).getDisplayTasksForm( appointment.getIdAppointment( ),
                        Appointment.APPOINTMENT_RESOURCE_TYPE, nIdAction, request, getLocale( request ), null );
                Map<String, Object> model = getModel( );
                model.put( MARK_TASKS_FORM, strHtmlTasksForm );
                model.put( PARAMETER_ID_ACTION, nIdAction );
                model.put( PARAMETER_REF_APPOINTMENT, refAppointment );

                return getXPage( TEMPLATE_TASKS_FORM_WORKFLOW, getLocale( request ), model );
            }

            return doProcessWorkflowAction( request );
        }
        return getMyAppointments( request );
    }

    /**
     * Do process a workflow action over an appointment
     *
     * @param request
     *            The request
     * @return The next URL to redirect to
     */
    @Action( ACTION_DO_PROCESS_WORKFLOW_ACTION )
    public synchronized XPage doProcessWorkflowAction( HttpServletRequest request ) throws UserNotSignedException
    {
        LuteceUser luteceUser = SecurityService.getInstance( ).getRegisteredUser( request );
        String strIdAction = request.getParameter( PARAMETER_ID_ACTION );
        String refAppointment = request.getParameter( PARAMETER_REF_APPOINTMENT );
        if ( StringUtils.isNotEmpty( strIdAction ) && StringUtils.isNumeric( strIdAction ) && StringUtils.isNotEmpty( refAppointment ) )
        {
            int nIdAction = Integer.parseInt( strIdAction );
            Appointment appointment = AppointmentService.findAppointmentByReference( refAppointment );
            // Check if an appointment Object was found and built with the given reference
            if( appointment == null )
            {
            	// When the appointment Object doesn't exist, we display an error message to the user
            	return getXPage( TEMPLATE_ERROR_APPOINTMENT_REFERENCE, getLocale( request ), null );
            }
            int nIdAppointment = appointment.getIdAppointment( );

            List<AppointmentSlot> listApptSlot = appointment.getListAppointmentSlot( );
            Slot slot = SlotService.findSlotById( listApptSlot.get( 0 ).getIdSlot( ) );

            if ( request.getParameter( PARAMETER_BACK ) == null )
            {
                try
                {
                    if ( WorkflowService.getInstance( ).isDisplayTasksForm( nIdAction, getLocale( request ) ) )
                    {
                        if ( WorkflowService.getInstance( ).canProcessAction( nIdAppointment, Appointment.APPOINTMENT_RESOURCE_TYPE, nIdAction,
                                slot.getIdForm( ), request, false, luteceUser ) )
                        {

                            String strError = WorkflowService.getInstance( ).doSaveTasksForm( nIdAppointment, Appointment.APPOINTMENT_RESOURCE_TYPE, nIdAction,
                                    slot.getIdForm( ), request, getLocale( request ), luteceUser );
                            if ( strError != null )
                            {
                                AppLogService.error( "Error Workflow:" + strError );
                                addError( strError );
                                return getWorkflowActionForm( request );
                            }
                        }
                        else
                        {

                            AppLogService.error( "Error Workflow can not process Action" );
                            addError( "Error Workflow can not process Action" );
                            return getMyAppointments( request );
                        }
                    }
                    else
                    {
                        ITaskService taskService = SpringContextService.getBean( TaskService.BEAN_SERVICE );
                        List<ITask> listActionTasks = taskService.getListTaskByIdAction( nIdAction, getLocale( request ) );
                        for ( ITask task : listActionTasks )
                        {
                            if ( task.getTaskType( ).getKey( ).equals( "taskChangeAppointmentStatus" ) && ( appointment.getIsCancelled( ) ) )
                            {
                                for ( AppointmentSlot apptSlt : listApptSlot )
                                {

                                    Slot slt = SlotService.findSlotById( apptSlt.getIdSlot( ) );

                                    if ( apptSlt.getNbPlaces( ) > slt.getNbRemainingPlaces( ) )
                                    {
                                        AppLogService.error( "Error Workflow:" + ERROR_MESSAGE_SLOT_FULL );
                                        addError( ERROR_MESSAGE_SLOT_FULL, getLocale( request ) );
                                        return getMyAppointments( request );

                                    }
                                }
                            }
                        }

                        WorkflowService.getInstance( ).doProcessAction( nIdAppointment, Appointment.APPOINTMENT_RESOURCE_TYPE, nIdAction, slot.getIdForm( ),
                                request, getLocale( request ), false, luteceUser );
                        AppointmentListenerManager.notifyAppointmentWFActionTriggered( nIdAppointment, nIdAction );

                    }
                    addInfo( MESSAGE_WF_ACTION_SUCESS, getLocale( request ) );
                }
                catch( Exception e )
                {
                    AppLogService.error( "Error Workflow", e );
                }

            }
        }
        return getMyAppointments( request );
    }

    /**
     * Get the captcha security service
     *
     * @return The captcha security service
     */
    private CaptchaSecurityService getCaptchaService( )
    {
        if ( _captchaSecurityService == null )
        {
            _captchaSecurityService = new CaptchaSecurityService( );
        }

        return _captchaSecurityService;
    }

    /**
     * Get the URL
     *
     * @param request
     *            Get the URL to cancel an appointment in FO
     * @param appointment
     *            The appointment
     * @return The URL to cancel the appointment
     */
    public static String getCancelAppointmentUrl( HttpServletRequest request, Appointment appointment )
    {
        UrlItem urlItem = new UrlItem( AppPathService.getProdUrl( request ) + AppPathService.getPortalUrl( ) );
        urlItem.addParameter( MVCUtils.PARAMETER_PAGE, XPAGE_NAME );
        urlItem.addParameter( MVCUtils.PARAMETER_VIEW, VIEW_GET_VIEW_CANCEL_APPOINTMENT );
        urlItem.addParameter( PARAMETER_REF_APPOINTMENT, appointment.getReference( ) );
        return urlItem.getUrl( );
    }

    /**
     * Get the URL
     *
     * @param request
     *            Get the URL to cancel an appointment in FO
     * @param appointment
     *            The appointment
     * @return The URL to cancel the appointment
     */
    public static String getCancelAppointmentUrl( Appointment appointment )
    {
        UrlItem urlItem = new UrlItem( AppPathService.getProdUrl( StringUtils.EMPTY ) + AppPathService.getPortalUrl( ) );
        urlItem.addParameter( MVCUtils.PARAMETER_PAGE, XPAGE_NAME );
        urlItem.addParameter( MVCUtils.PARAMETER_VIEW, VIEW_GET_VIEW_CANCEL_APPOINTMENT );
        urlItem.addParameter( PARAMETER_REF_APPOINTMENT, appointment.getReference( ) );
        return urlItem.getUrl( );
    }

    /**
     * Get the URL
     *
     * @param request
     *            Get the URL to report an appointment in FO
     * @param appointment
     *            The appointment
     * @return The URL to report the appointment
     */
    public static String getReportAppointmentUrl( AppointmentDTO appointment )
    {
        UrlItem urlItem = new UrlItem( AppPathService.getProdUrl( StringUtils.EMPTY ) + AppPathService.getPortalUrl( ) );
        urlItem.addParameter( MVCUtils.PARAMETER_PAGE, XPAGE_NAME );
        urlItem.addParameter( MVCUtils.PARAMETER_VIEW, VIEW_APPOINTMENT_CALENDAR );
        urlItem.addParameter( PARAMETER_ID_FORM, appointment.getIdForm( ) );
        urlItem.addParameter( PARAMETER_NB_PLACE_TO_TAKE, appointment.getNbBookedSeats( ) );
        urlItem.addParameter( PARAMETER_REF_APPOINTMENT, appointment.getReference( ) );
        return urlItem.getUrl( );
    }

    /**
     * Set the user infos to the appointment DTO
     *
     * @param request
     *            the request
     * @param appointment
     *            the appointment DTO
     */
    public void setUserInfo( HttpServletRequest request, AppointmentDTO appointment )
    {
        if ( SecurityService.isAuthenticationEnable( ) && ( appointment != null ) )
        {
            LuteceUser user = SecurityService.getInstance( ).getRegisteredUser( request );

            if ( user != null )
            {
                appointment.setGuid( user.getName( ) );
                appointment.setFirstName( user.getUserInfo( AppPropertiesService.getProperty( PROPERTY_USER_ATTRIBUTE_FIRST_NAME, StringUtils.EMPTY ) ) );
                appointment.setEmail( user.getUserInfo( AppPropertiesService.getProperty( PROPERTY_USER_ATTRIBUTE_EMAIL, StringUtils.EMPTY ) ) );
                String lastName = user.getUserInfo( AppPropertiesService.getProperty( PROPERTY_USER_ATTRIBUTE_PREFERED_NAME, StringUtils.EMPTY ) );
                if ( ( lastName == null ) || lastName.isEmpty( ) )
                {
                    lastName = user.getUserInfo( AppPropertiesService.getProperty( PROPERTY_USER_ATTRIBUTE_LAST_NAME, StringUtils.EMPTY ) );
                }
                appointment.setLastName( lastName );
            }
        }
    }

    /**
     * check if authentication
     *
     * @param form
     *            Form
     * @param request
     *            HttpServletRequest
     * @throws UserNotSignedException
     *             exception if the form requires an authentication and the user is not logged
     */
    private void checkMyLuteceAuthentication( AppointmentFormDTO form, HttpServletRequest request ) throws UserNotSignedException, AccessDeniedException
    {
        if ( !SecurityService.isAuthenticationEnable( ) )
        {
            return;
        }

        // Try to register the user in case of external authentication
        SecurityService securityService = SecurityService.getInstance( );
        if ( securityService.isExternalAuthentication( ) )
        {
            if ( form.getActiveAuthentication( ) )
            {
                // The authentication is external
                // Should register the user if it's not already done
                if ( securityService.getRegisteredUser( request ) == null && securityService.getRemoteUser( request ) == null )
                {
                    // Authentication is required to access to the portal
                    throw new UserNotSignedException( );
                }

                if ( !Form.ROLE_NONE.equals( form.getRole( ) ) && !securityService.isUserInRole( request, form.getRole( ) ) )
                {
                    // User must have the right role
                    throw new AccessDeniedException( "Unauthorized" );
                }
            }
        }
        else
        {
            // If portal authentication is enabled and user is null and the
            // requested URL
            // is not the login URL, user cannot access to Portal
            if ( form.getActiveAuthentication( ) && securityService.getRegisteredUser( request ) == null && !securityService.isLoginUrl( request ) )
            {
                // Authentication is required to access to the portal
                throw new UserNotSignedException( );
            }
        }
    }

    /**
     * Check if the date are in the validity date display range of the form
     *
     * @param date
     *            the starting date of slot
     * @param locale
     *            the locale
     * @return true if the starting date of slot is displayed on FO
     */
    private boolean isAuthorizedDate( LocalDateTime date, Locale locale )
    {
        // Get the min time from now before a user can take an appointment (in hours)
        LocalDateTime startingDateOfDisplay = LocalDateTime.now( ).plusHours( _appointmentForm.getMinTimeBeforeAppointment( ) );
        // validity date range of the form
        LocalDateTime startingValidityDate = _appointmentForm.getDateStartValidity( ).toLocalDate( ).atStartOfDay( );
        if ( startingValidityDate.isAfter( startingDateOfDisplay ) )
        {
            startingDateOfDisplay = startingValidityDate;
        }
        // Calculate the ending date of display with the nb weeks to display since today
        // We calculate the number of weeks including the current week, so it and will end to the (n) next sunday
        LocalDate endingDateOfDisplay = startingDateOfDisplay.toLocalDate( ).with( WeekFields.of( locale ).dayOfWeek( ), DayOfWeek.SUNDAY.getValue( ) )
                .plusWeeks( (long) _appointmentForm.getNbWeeksToDisplay( ) - 1 );
        return !( date.toLocalDate( ).isAfter( endingDateOfDisplay ) || date.isBefore( startingDateOfDisplay ) );

    }
}
