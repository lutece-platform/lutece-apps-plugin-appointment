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

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

import javax.servlet.http.HttpServletRequest;

import fr.paris.lutece.api.user.User;
import fr.paris.lutece.plugins.appointment.business.calendar.CalendarTemplateHome;
import fr.paris.lutece.plugins.appointment.service.AppointmentResourceIdService;
import fr.paris.lutece.plugins.appointment.service.AppointmentUtilities;
import fr.paris.lutece.plugins.appointment.service.CategoryService;
import fr.paris.lutece.plugins.appointment.web.dto.AppointmentFormDTO;
import fr.paris.lutece.plugins.appointment.web.dto.ReservationRuleDTO;
import fr.paris.lutece.portal.business.role.RoleHome;
import fr.paris.lutece.portal.business.user.AdminUser;
import fr.paris.lutece.portal.service.captcha.CaptchaSecurityService;
import fr.paris.lutece.portal.service.mailinglist.AdminMailingListService;
import fr.paris.lutece.portal.service.plugin.Plugin;
import fr.paris.lutece.portal.service.plugin.PluginService;
import fr.paris.lutece.portal.service.rbac.RBACService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import fr.paris.lutece.portal.service.workflow.WorkflowService;
import fr.paris.lutece.portal.service.workgroup.AdminWorkgroupService;
import fr.paris.lutece.portal.util.mvc.admin.MVCAdminJspBean;
import fr.paris.lutece.portal.web.util.LocalizedPaginator;
import fr.paris.lutece.util.ReferenceList;
import fr.paris.lutece.util.html.AbstractPaginator;
import fr.paris.lutece.util.url.UrlItem;

public abstract class AbstractAppointmentFormAndSlotJspBean extends MVCAdminJspBean
{

    /**
     * 
     */
    private static final long serialVersionUID = 7709182167218092169L;
    protected static final String PARAMETER_ERROR_MODIFICATION = "error_modification";
    protected static final String ERROR_MESSAGE_TIME_START_AFTER_TIME_END = "appointment.message.error.timeStartAfterTimeEnd";
    protected static final String ERROR_MESSAGE_TIME_START_AFTER_DATE_END = "appointment.message.error.dateStartAfterTimeEnd";
    protected static final String ERROR_MESSAGE_NO_WORKING_DAY_CHECKED = "appointment.message.error.noWorkingDayChecked";
    protected static final String ERROR_MESSAGE_APPOINTMENT_SUPERIOR_MIDDLE = "appointment.message.error.formatDaysBeforeAppointmentMiddleSuperior";
    protected static final String ERROR_MESSAGE_WEEK_IS_OPEN_FO = "appointment.modifyCalendarSlots.errorWeekIsOpenFo";
    protected static final String MESSAGE_ERROR_DAY_DURATION_APPOINTMENT_NOT_MULTIPLE_FORM = "appointment.message.error.durationAppointmentDayNotMultipleForm";
    private static final String MESSAGE_ERROR_NUMBER_OF_SEATS_BOOKED = "appointment.message.error.numberOfSeatsBookedAndConcurrentAppointments";
    private static final String MESSAGE_MULTI_SLOT_ERROR_NUMBER_OF_SEATS_BOOKED = "appointment.message.error.multiSlot.numberOfSeatsBookedAndConcurrentAppointments";
    private static final String MESSAGE_ERROR_NUMBER_DAY_BETWEEN_TWO_APPOINTMENTS = "appointment.message.error.nbDaysBetweenTwoAppointments";
    private static final String MESSAGE_ERROR_MAX_APPOINTMENTS_PER_USER = "appointment.message.error.nbMaxAppointmentsPerUser";

    // Constantes
    protected static final String VAR_CAP = "var_cap";
    protected static final String NEW_CAP = "new_cap";

    // Properties
    private static final String PROPERTY_DEFAULT_LIST_ITEM_PER_PAGE = "appointment.listItems.itemsPerPage";
    protected static final String PARAMETER_CAPACITY_MOD = "capacity";
    private static final String PROPERTY_MODULE_APPOINTMENT_RESOURCE_NAME = "appointment.moduleAppointmentResource.name";
    private static final String PROPERTY_MODULE_APPOINTMENT_DESK_NAME = "appointment.moduleAppointmentDesk.name";
    // Parameters
    private static final String PARAMETER_PAGE_INDEX = "page_index";

    // Markers
    private static final String MARK_PAGINATOR = "paginator";
    private static final String MARK_NB_ITEMS_PER_PAGE = "nb_items_per_page";
    private static final String MARK_USER_WORKGROUP_REF_LIST = "user_workgroup_list";
    private static final String MARK_APPOINTMENT_RESOURCE_ENABLED = "isResourceInstalled";
    private static final String MARK_APPOINTMENT_DESK_ENABLED = "isDeskInstalled";
    private static final String MARK_MAILING_LIST = "mailing_list";
    private static final String MARK_APPOINTMENT_FORM = "appointmentform";
    private static final String MARK_LIST_WORKFLOWS = "listWorkflows";
    private static final String MARK_IS_CAPTCHA_ENABLED = "isCaptchaEnabled";
    private static final String MARK_REF_LIST_CALENDAR_TEMPLATES = "refListCalendarTemplates";
    private static final String MARK_REF_LIST_ROLES = "refListRoles";
    private static final String MARK_LIST_CATEGORIES = "listCategories";
    protected static final String MARK_LOCALE = "language";
    private static final String MARK_REF_LIST_DAYS_WEEK = "refListDaysWeek";

    // Variables
    private static final CaptchaSecurityService _captchaSecurityService = new CaptchaSecurityService( );
    private String _strCurrentPageIndex;
    private int _nItemsPerPage;

    /**
     * Check Constraints
     * 
     * @param appointmentForm
     * @return the boolean
     */
    protected boolean checkConstraints( AppointmentFormDTO appointmentForm )
    {
        return checkStartingAndEndingTime( appointmentForm ) && checkStartingAndEndingValidityDate( appointmentForm )
                && checkSlotCapacityAndPeoplePerAppointment( appointmentForm ) && checkAtLeastOneWorkingDayOpen( appointmentForm )
                && checkMultiSlotFormTypeBookablePlaces( appointmentForm ) && checkControlMaxAppointmentsPerUser( appointmentForm );
    }

    /**
     * Return a model that contains the list and paginator infos
     * 
     * @param request
     *            The HTTP request
     * @param strBookmark
     *            The bookmark
     * @param list
     *            The list of item
     * @param strManageJsp
     *            The JSP
     * @return The model
     */
    protected <T> Map<String, Object> getPaginatedListModel( HttpServletRequest request, String strBookmark, List<T> list, String strManageJsp )
    {
        int nDefaultItemsPerPage = AppPropertiesService.getPropertyInt( PROPERTY_DEFAULT_LIST_ITEM_PER_PAGE, 50 );
        _strCurrentPageIndex = AbstractPaginator.getPageIndex( request, AbstractPaginator.PARAMETER_PAGE_INDEX, _strCurrentPageIndex );
        _nItemsPerPage = AbstractPaginator.getItemsPerPage( request, AbstractPaginator.PARAMETER_ITEMS_PER_PAGE, _nItemsPerPage, nDefaultItemsPerPage );

        UrlItem url = new UrlItem( strManageJsp );
        String strUrl = url.getUrl( );

        // PAGINATOR
        LocalizedPaginator<T> paginator = new LocalizedPaginator<>( list, _nItemsPerPage, strUrl, PARAMETER_PAGE_INDEX, _strCurrentPageIndex, getLocale( ) );

        Map<String, Object> model = getModel( );

        model.put( MARK_NB_ITEMS_PER_PAGE, String.valueOf( _nItemsPerPage ) );
        model.put( MARK_PAGINATOR, paginator );
        model.put( strBookmark, paginator.getPageItems( ) );

        return model;
    }

    /**
     * Check that the user has checked as least one working day on its form
     * 
     * @param appointmentForm
     *            the appointForm DTO
     * @return true if at least one working day is checked, false otherwise
     */
    private boolean checkAtLeastOneWorkingDayOpen( AppointmentFormDTO appointmentForm )
    {
        boolean bReturn = true;
        if ( !( appointmentForm.getIsOpenMonday( ) || appointmentForm.getIsOpenTuesday( ) || appointmentForm.getIsOpenWednesday( )
                || appointmentForm.getIsOpenThursday( ) || appointmentForm.getIsOpenFriday( ) || appointmentForm.getIsOpenSaturday( )
                || appointmentForm.getIsOpenSunday( ) ) )
        {
            bReturn = false;
            addError( ERROR_MESSAGE_NO_WORKING_DAY_CHECKED, getLocale( ) );
        }
        return bReturn;
    }

    /**
     * Check the starting time and the ending time of the appointmentFormDTO
     * 
     * @param appointmentForm
     *            the appointmentForm DTO
     * @return false if there is an error
     */
    private boolean checkStartingAndEndingTime( AppointmentFormDTO appointmentForm )
    {
        boolean bReturn = true;
        LocalTime startingTime = LocalTime.parse( appointmentForm.getTimeStart( ) );
        LocalTime endingTime = LocalTime.parse( appointmentForm.getTimeEnd( ) );
        if ( startingTime.isAfter( endingTime ) )
        {
            bReturn = false;
            addError( ERROR_MESSAGE_TIME_START_AFTER_TIME_END, getLocale( ) );
        }
        long lMinutes = startingTime.until( endingTime, ChronoUnit.MINUTES );
        if ( appointmentForm.getDurationAppointments( ) > lMinutes )
        {
            bReturn = false;
            addError( ERROR_MESSAGE_APPOINTMENT_SUPERIOR_MIDDLE, getLocale( ) );
        }
        if ( ( lMinutes % appointmentForm.getDurationAppointments( ) ) != 0 )
        {
            bReturn = false;
            addError( MESSAGE_ERROR_DAY_DURATION_APPOINTMENT_NOT_MULTIPLE_FORM, getLocale( ) );
        }
        return bReturn;
    }

    /**
     * Check the starting and the ending validity date of the appointmentForm DTO
     * 
     * @param appointmentForm
     *            the appointmentForm DTO
     * @return false if there is an error
     */
    protected boolean checkStartingAndEndingValidityDate( AppointmentFormDTO appointmentForm )
    {
        boolean bReturn = true;
        if ( appointmentForm.getDateStartValidity( ) != null && appointmentForm.getDateEndValidity( ) != null
                && appointmentForm.getDateStartValidity( ).toLocalDate( ).isAfter( appointmentForm.getDateEndValidity( ).toLocalDate( ) ) )
        {
            bReturn = false;
            addError( ERROR_MESSAGE_TIME_START_AFTER_DATE_END, getLocale( ) );
        }
        return bReturn;
    }

    /**
     * Check the slot capacity and the max people per appointment of the appointmentForm DTO
     * 
     * @param appointmentForm
     *            the appointmentForm DTO
     * @return false if the maximum number of people per appointment is bigger than the maximum capacity of the slot
     */
    protected boolean checkSlotCapacityAndPeoplePerAppointment( AppointmentFormDTO appointmentForm )
    {
        boolean bReturn = true;
        if ( appointmentForm.getMaxPeoplePerAppointment( ) > appointmentForm.getMaxCapacityPerSlot( ) && !appointmentForm.getBoOverbooking( ) )
        {
            bReturn = false;
            addError( MESSAGE_ERROR_NUMBER_OF_SEATS_BOOKED, getLocale( ) );
        }
        return bReturn;
    }

    /**
     * check the number of bookable places will be set to 1 and cann't be modified, when creating a "multi-slot form"
     * 
     * @param appointmentForm
     *            the appointmentForm DTO
     * @return false if the form type is "multi-slot" and Max people Per Slot is not set to 1
     */
    protected boolean checkMultiSlotFormTypeBookablePlaces( AppointmentFormDTO appointmentForm )
    {
        boolean bReturn = true;
        if ( appointmentForm.getIsMultislotAppointment( ) && appointmentForm.getMaxPeoplePerAppointment( ) != 1 )
        {
            bReturn = false;
            addError( MESSAGE_MULTI_SLOT_ERROR_NUMBER_OF_SEATS_BOOKED, getLocale( ) );
        }
        return bReturn;
    }

    /**
     * Check that the email is required if the value of max appointments on a defined period or delay between two appointments for the same use is not equal to
     * 0
     * 
     * @param appointmentForm
     *            the appointmentForm DTO
     * @return false if email is not required and the value of max appointments on a defined period or delay between two appointments for the same use is not
     *         equal to 0
     */
    protected boolean checkControlMaxAppointmentsPerUser( AppointmentFormDTO appointmentForm )
    {
        boolean bReturn = true;
        if ( appointmentForm.getNbDaysBeforeNewAppointment( ) != 0 && !appointmentForm.getEnableMandatoryEmail( ) )
        {
            bReturn = false;
            addError( MESSAGE_ERROR_NUMBER_DAY_BETWEEN_TWO_APPOINTMENTS, getLocale( ) );
        }
        if ( appointmentForm.getNbMaxAppointmentsPerUser( ) != 0 && !appointmentForm.getEnableMandatoryEmail( ) )
        {
            bReturn = false;
            addError( MESSAGE_ERROR_MAX_APPOINTMENTS_PER_USER, getLocale( ) );
        }
        return bReturn;
    }

    /**
     * Valdate rule bean
     * 
     * @param request
     * @param strPrefix
     * @return true if validated otherwise false
     */
    protected boolean validateReservationRuleBean( HttpServletRequest request, String strPrefix )
    {

        ReservationRuleDTO rule = new ReservationRuleDTO( );
        populate( rule, request );
        return validateBean( rule, strPrefix );

    }

    /**
     * Valdate rule bean
     * 
     * @param appointmentForm
     * @param strPrefix
     * @return true if validated otherwise false
     */
    protected boolean validateReservationRuleBean( AppointmentFormDTO appointmentForm, String strPrefix )
    {

        ReservationRuleDTO rule = new ReservationRuleDTO( );
        AppointmentUtilities.fillInReservationRuleAdvancedParam( rule, appointmentForm );

        return validateBean( rule, strPrefix );

    }

    /**
     * Add elements to the model to display the left column to modify an appointment form
     * 
     * @param request
     *            The request to store the appointment form in session
     * @param appointmentForm
     *            The appointment form
     * @param user
     *            The user
     * @param locale
     *            The locale
     * @param model
     *            the model to add elements in
     */
    public static void addElementsToModel( AppointmentFormDTO appointmentForm, AdminUser user, Locale locale, Map<String, Object> model )
    {
        Plugin pluginAppointmentResource = PluginService.getPlugin( AppPropertiesService.getProperty( PROPERTY_MODULE_APPOINTMENT_RESOURCE_NAME ) );
        Plugin moduleAppointmentDesk = PluginService.getPlugin( AppPropertiesService.getProperty( PROPERTY_MODULE_APPOINTMENT_DESK_NAME ) );
        ReferenceList listRoles = RoleHome.getRolesList( user );
        ReferenceList listDaysWeek = new ReferenceList( 7 );
        IntStream.rangeClosed( 1, 7 ).forEach( i -> listDaysWeek.addItem( i, Integer.toString( i ) ) );
        model.put( MARK_APPOINTMENT_FORM, appointmentForm );
        model.put( MARK_LOCALE, locale );
        model.put( MARK_LIST_WORKFLOWS, WorkflowService.getInstance( ).getWorkflowsEnabled( (User) user, locale ) );
        model.put( MARK_IS_CAPTCHA_ENABLED, _captchaSecurityService.isAvailable( ) );
        model.put( MARK_REF_LIST_CALENDAR_TEMPLATES, CalendarTemplateHome.findAllInReferenceList( ) );
        model.put( MARK_LIST_CATEGORIES, CategoryService.findAllInReferenceList( ) );
        model.put( MARK_USER_WORKGROUP_REF_LIST, AdminWorkgroupService.getUserWorkgroups( user, locale ) );
        model.put( MARK_APPOINTMENT_RESOURCE_ENABLED, ( pluginAppointmentResource != null ) && pluginAppointmentResource.isInstalled( ) );
        model.put( MARK_APPOINTMENT_DESK_ENABLED, ( moduleAppointmentDesk != null ) && moduleAppointmentDesk.isInstalled( ) );
        model.put( MARK_REF_LIST_ROLES, listRoles );
        model.put( MARK_MAILING_LIST, AdminMailingListService.getMailingLists( user ) );
        model.put( AppointmentUtilities.MARK_PERMISSION_ADD_COMMENT, String.valueOf( RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE,
                String.valueOf( appointmentForm.getIdForm( ) ), AppointmentResourceIdService.PERMISSION_ADD_COMMENT_FORM, (User) user ) ) );
        model.put( AppointmentUtilities.MARK_PERMISSION_MODERATE_COMMENT, String.valueOf( RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE,
                String.valueOf( appointmentForm.getIdForm( ) ), AppointmentResourceIdService.PERMISSION_MODERATE_COMMENT_FORM, (User) user ) ) );
        model.put( AppointmentUtilities.MARK_PERMISSION_ACCESS_CODE, user.getAccessCode( ) );
        model.put( MARK_REF_LIST_DAYS_WEEK, listDaysWeek );
    }

}
