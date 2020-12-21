/*
 * Copyright (c) 2002-2020, City of Paris
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
package fr.paris.lutece.plugins.appointment.service;

import static java.lang.Math.toIntExact;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolation;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import fr.paris.lutece.plugins.appointment.business.appointment.Appointment;
import fr.paris.lutece.plugins.appointment.business.appointment.AppointmentSlot;
import fr.paris.lutece.plugins.appointment.business.form.Form;
import fr.paris.lutece.plugins.appointment.business.planning.TimeSlot;
import fr.paris.lutece.plugins.appointment.business.planning.WeekDefinition;
import fr.paris.lutece.plugins.appointment.business.planning.WorkingDay;
import fr.paris.lutece.plugins.appointment.business.planning.WorkingDayHome;
import fr.paris.lutece.plugins.appointment.business.rule.ReservationRule;
import fr.paris.lutece.plugins.appointment.business.slot.Slot;
import fr.paris.lutece.plugins.appointment.business.user.User;
import fr.paris.lutece.plugins.appointment.service.lock.SlotEditTask;
import fr.paris.lutece.plugins.appointment.service.lock.TimerForLockOnSlot;
import fr.paris.lutece.plugins.appointment.web.dto.AppointmentDTO;
import fr.paris.lutece.plugins.appointment.web.dto.AppointmentFilterDTO;
import fr.paris.lutece.plugins.appointment.web.dto.AppointmentFormDTO;
import fr.paris.lutece.plugins.appointment.web.dto.ResponseRecapDTO;
import fr.paris.lutece.plugins.genericattributes.business.Entry;
import fr.paris.lutece.plugins.genericattributes.business.EntryFilter;
import fr.paris.lutece.plugins.genericattributes.business.EntryHome;
import fr.paris.lutece.plugins.genericattributes.business.GenericAttributeError;
import fr.paris.lutece.plugins.genericattributes.business.Response;
import fr.paris.lutece.plugins.genericattributes.service.entrytype.EntryTypeServiceManager;
import fr.paris.lutece.plugins.genericattributes.service.entrytype.IEntryTypeService;
import fr.paris.lutece.plugins.genericattributes.util.GenericAttributesUtils;
import fr.paris.lutece.portal.business.user.AdminUser;
import fr.paris.lutece.portal.service.i18n.I18nService;
import fr.paris.lutece.portal.service.rbac.RBACService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import fr.paris.lutece.util.beanvalidation.BeanValidationUtil;

/**
 * Utility class for Appointment Mutualize methods between MVCApplication and MVCAdminJspBean
 * 
 * @author Laurent Payen
 *
 */
public final class AppointmentUtilities
{

	public static final String ERROR_MESSAGE_EMPTY_CONFIRM_EMAIL = "appointment.validation.appointment.EmailConfirmation.email";
    public static final String ERROR_MESSAGE_CONFIRM_EMAIL = "appointment.message.error.confirmEmail";
    public static final String ERROR_MESSAGE_DATE_APPOINTMENT = "appointment.message.error.dateAppointment";
    public static final String ERROR_MESSAGE_EMPTY_EMAIL = "appointment.validation.appointment.Email.notEmpty";
    public static final String ERROR_MESSAGE_EMPTY_NB_BOOKED_SEAT = "appointment.validation.appointment.NbBookedSeat.notEmpty";
    public static final String ERROR_MESSAGE_FORMAT_NB_BOOKED_SEAT = "appointment.validation.appointment.NbBookedSeat.notNumberFormat";
    public static final String ERROR_MESSAGE_ERROR_NB_BOOKED_SEAT = "appointment.validation.appointment.NbBookedSeat.error";

    public static final String SESSION_TIMER_SLOT = "appointment.session.timer.slot";

    public static final String PROPERTY_DEFAULT_EXPIRED_TIME_EDIT_APPOINTMENT = "appointment.edit.expired.time";

    public static final int THIRTY_MINUTES = 30;

    /**
     * Private constructor - this class does not need to be instantiated
     */
    private AppointmentUtilities( )
    {
    }

    /**
     * Check that the email is correct and matches the confirm email
     * 
     * @param strEmail
     *            the email
     * @param strConfirmEmail
     *            the confirm email
     * @param form
     *            the form
     * @param locale
     *            the local
     * @param listFormErrors
     *            the list of errors that can be fill in with the errors found for the email
     */
    public static void checkEmail( String strEmail, String strConfirmEmail, AppointmentFormDTO form, Locale locale, List<GenericAttributeError> listFormErrors )
    {
        if ( form.getEnableMandatoryEmail( ) )
        {
            if ( StringUtils.isEmpty( strEmail ) )
            {
                GenericAttributeError genAttError = new GenericAttributeError( );
                genAttError.setErrorMessage( I18nService.getLocalizedString( ERROR_MESSAGE_EMPTY_EMAIL, locale ) );
                listFormErrors.add( genAttError );
            }
            if ( StringUtils.isEmpty( strConfirmEmail ) )
            {
                GenericAttributeError genAttError = new GenericAttributeError( );
                genAttError.setErrorMessage( I18nService.getLocalizedString( ERROR_MESSAGE_EMPTY_CONFIRM_EMAIL, locale ) );
                listFormErrors.add( genAttError );
            }
        }
        if ( !StringUtils.equals( strEmail, strConfirmEmail ) )
        {
            GenericAttributeError genAttError = new GenericAttributeError( );
            genAttError.setErrorMessage( I18nService.getLocalizedString( ERROR_MESSAGE_CONFIRM_EMAIL, locale ) );
            listFormErrors.add( genAttError );
        }
    }

    /**
     * Check that the date of the appointment we try to take is not in the past
     * 
     * @param appointmentDTO
     *            the appointment
     * @param locale
     *            the local
     * @param listFormErrors
     *            the list of errors that can be fill in with the error found with the date
     */
    public static void checkDateOfTheAppointmentIsNotBeforeNow( AppointmentDTO appointmentDTO, Locale locale, List<GenericAttributeError> listFormErrors )
    {
        if ( getStartingDateTime( appointmentDTO ).toLocalDate( ).isBefore( LocalDate.now( ) ) )
        {
            GenericAttributeError genAttError = new GenericAttributeError( );
            genAttError.setErrorMessage( I18nService.getLocalizedString( ERROR_MESSAGE_DATE_APPOINTMENT, locale ) );
            listFormErrors.add( genAttError );
        }
    }

    /**
     * Check that the delay between two appointments for the same use has been respected
     * 
     * @param appointmentDTO
     *            the appointment
     * @param strEmail
     *            the email
     * @param form
     *            the form
     * @return false if the delay is not respected
     */
    public static boolean checkNbDaysBetweenTwoAppointments( AppointmentDTO appointmentDTO, String strFirstName, String strLastName, String strEmail,
            AppointmentFormDTO form )
    {
        boolean bCheckPassed = true;
        int nbDaysBetweenTwoAppointments = form.getNbDaysBeforeNewAppointment( );
        if ( nbDaysBetweenTwoAppointments != 0 )
        {
            List<Slot> listSlots = getSlotsByEmail( strEmail, appointmentDTO.getIdAppointment( ) );
            if ( CollectionUtils.isNotEmpty( listSlots ) )
            {
                // Get the last appointment date for this form
                listSlots = listSlots.stream( ).filter( s -> s.getIdForm( ) == form.getIdForm( ) ).collect( Collectors.toList( ) );
                if ( CollectionUtils.isNotEmpty( listSlots ) )
                {
                    LocalDate dateOfTheLastAppointment = listSlots.stream( ).map( Slot::getStartingDateTime ).max( LocalDateTime::compareTo ).get( )
                            .toLocalDate( );
                    // Check the number of days between this appointment and
                    // the last appointment the user has taken
                    LocalDate dateOfTheAppointment = getStartingDateTime( appointmentDTO ).toLocalDate( );
                    if ( Math.abs( dateOfTheLastAppointment.until( dateOfTheAppointment, ChronoUnit.DAYS ) ) <= nbDaysBetweenTwoAppointments )
                    {
                        bCheckPassed = false;
                    }
                }
            }
        }
        return bCheckPassed;
    }

    /**
     * Check that the delay between two appointments for the same use has been respected
     * 
     * @param appointmentDTO
     *            the appointment
     * @param strEmail
     *            the email
     * @param form
     *            the form
     * @return false if the delay is not respected
     */
    public static boolean checkNbDaysBetweenTwoAppointmentsTaken( AppointmentDTO appointmentDTO, String strEmail, AppointmentFormDTO form )
    {
        boolean bCheckPassed = true;
        int nbDaysBetweenTwoAppointments = form.getNbDaysBeforeNewAppointment( );
        if ( nbDaysBetweenTwoAppointments != 0 )
        {
            AppointmentFilterDTO filter = new AppointmentFilterDTO( );
            filter.setEmail( strEmail );
            filter.setStatus( 0 );
            filter.setIdForm( form.getIdForm( ) );
            List<Appointment> listAppointment = AppointmentService.findListAppointmentsByFilter( filter );
            // If we modify an appointment, we remove the
            // appointment that we currently edit
            if ( appointmentDTO.getIdAppointment( ) != 0 )
            {
                listAppointment = listAppointment.stream( ).filter( a -> a.getIdAppointment( ) != appointmentDTO.getIdAppointment( ) )
                        .collect( Collectors.toList( ) );
            }

            if ( CollectionUtils.isNotEmpty( listAppointment ) )
            {

                LocalDateTime dateOfTheLastAppointmentTaken = listAppointment.stream( ).map( Appointment::getDateAppointmentTaken )
                        .max( LocalDateTime::compareTo ).get( );

                if ( Math.abs( dateOfTheLastAppointmentTaken.until( LocalDateTime.now( ), ChronoUnit.DAYS ) ) <= nbDaysBetweenTwoAppointments )
                {
                    bCheckPassed = false;
                }

            }
        }
        return bCheckPassed;
    }

    /**
     * Get the appointment of a user appointment
     * 
     * @param strEmail
     *            the user's email
     * @param idAppointment
     *            the id of the appointment
     * @return the list of appointment
     */
    private static List<Appointment> getAppointmentByEmail( String strEmail, int idAppointment )
    {
        List<Appointment> listAppointment = new ArrayList<>( );
        if ( StringUtils.isNotEmpty( strEmail ) )
        {
            // Looking for existing users with this email
            List<User> listUsers = UserService.findUsersByEmail( strEmail );
            if ( listUsers != null )
            {
                // For each User
                for ( User user : listUsers )
                {
                    // looking for its appointment
                    listAppointment.addAll( AppointmentService.findListAppointmentByUserId( user.getIdUser( ) ) );
                }

                // If we modify an appointment, we remove the
                // appointment that we currently edit
                if ( idAppointment != 0 )
                {
                    listAppointment = listAppointment.stream( ).filter( a -> a.getIdAppointment( ) != idAppointment ).collect( Collectors.toList( ) );
                }

            }
        }
        return listAppointment;
    }

    /**
     * Get the slot of a user appointment
     * 
     * @param strEmail
     *            the user's email
     * @param idAppointment
     *            the id of the appointment
     * @return the list of slots
     */
    private static List<Slot> getSlotsByEmail( String strEmail, int idAppointment )
    {
        List<Slot> listSlots = new ArrayList<>( );
        if ( StringUtils.isNotEmpty( strEmail ) )
        {
            List<Appointment> listAppointment = getAppointmentByEmail( strEmail, idAppointment );
            if ( CollectionUtils.isNotEmpty( listAppointment ) )
            {
                // I know we could have a join sql query, but I don't
                // want to join the appointment table with the slot
                // table, it's too big and not efficient

                for ( Appointment appointment : listAppointment )
                {
                    if ( !appointment.getIsCancelled( ) )
                    {
                    	   listSlots = SlotService.findListSlotByIdAppointment( appointment.getIdAppointment( ) );
                    }
                }

            }

        }
        return listSlots;
    }

    /**
     * Check that the number of appointments on a defined period is not above the maximum authorized
     * 
     * @param appointmentDTO
     *            the appointment
     * @param strEmail
     *            the email of the user
     * @param form
     *            the form
     * @return false if the number of appointments is above the maximum authorized on the defined period
     */
    public static boolean checkNbMaxAppointmentsOnAGivenPeriod( AppointmentDTO appointmentDTO, String strEmail, AppointmentFormDTO form )
    {
        boolean bCheckPassed = true;
        int nbMaxAppointmentsPerUser = form.getNbMaxAppointmentsPerUser( );
        int nbDaysForMaxAppointmentsPerUser = form.getNbDaysForMaxAppointmentsPerUser( );
        if ( nbMaxAppointmentsPerUser != 0 )
        {
            List<Slot> listSlots = getSlotsByEmail( strEmail, appointmentDTO.getIdAppointment( ) );
            if ( CollectionUtils.isNotEmpty( listSlots ) )
            {
                // Filter fot the good form
                listSlots = listSlots.stream( ).filter( s -> s.getIdForm( ) == form.getIdForm( ) ).collect( Collectors.toList( ) );
                if ( CollectionUtils.isNotEmpty( listSlots ) )
                {
                    // Get the date of the future appointment
                    LocalDate dateOfTheAppointment = getStartingDateTime( appointmentDTO ).toLocalDate( );
                    // Min starting date of the period
                    LocalDate minStartingDateOfThePeriod = dateOfTheAppointment.minusDays( nbDaysForMaxAppointmentsPerUser );
                    // Max ending date of the period
                    LocalDate maxEndingDateOfThePeriod = dateOfTheAppointment.plusDays( nbDaysForMaxAppointmentsPerUser );
                    // Keep only the slots that are in the min-max period
                    listSlots = listSlots.stream( )
                            .filter( s -> s.getStartingDateTime( ).toLocalDate( ).isEqual( minStartingDateOfThePeriod )
                                    || s.getStartingDateTime( ).toLocalDate( ).isAfter( minStartingDateOfThePeriod ) )
                            .filter( s -> s.getStartingDateTime( ).toLocalDate( ).isEqual( maxEndingDateOfThePeriod )
                                    || s.getStartingDateTime( ).toLocalDate( ).isBefore( maxEndingDateOfThePeriod ) )
                            .collect( Collectors.toList( ) );
                    LocalDate startingDateOfThePeriod = null;
                    LocalDate endingDateOfThePeriod = null;
                    // For each slot
                    for ( Slot slot : listSlots )
                    {
                        if ( slot.getStartingDateTime( ).toLocalDate( ).isBefore( dateOfTheAppointment ) )
                        {
                            startingDateOfThePeriod = slot.getStartingDateTime( ).toLocalDate( );
                            endingDateOfThePeriod = startingDateOfThePeriod.plusDays( nbDaysForMaxAppointmentsPerUser );
                        }
                        if ( slot.getStartingDateTime( ).toLocalDate( ).isAfter( dateOfTheAppointment ) )
                        {
                            endingDateOfThePeriod = slot.getStartingDateTime( ).toLocalDate( );
                            startingDateOfThePeriod = endingDateOfThePeriod.minusDays( nbDaysForMaxAppointmentsPerUser );
                        }
                        if ( slot.getStartingDateTime( ).toLocalDate( ).isEqual( dateOfTheAppointment ) )
                        {
                            startingDateOfThePeriod = endingDateOfThePeriod = slot.getStartingDateTime( ).toLocalDate( );
                        }
                        // Check the number of slots on the period
                        final LocalDate startingDateOfPeriodToSearch = startingDateOfThePeriod;
                        final LocalDate endingDateOfPeriodToSearch = endingDateOfThePeriod;
                        int nbSlots = toIntExact( listSlots.stream( )
                                .filter( s -> ( s.getStartingDateTime( ).toLocalDate( ).equals( startingDateOfPeriodToSearch )
                                        || s.getStartingDateTime( ).toLocalDate( ).isAfter( startingDateOfPeriodToSearch ) )
                                        && ( s.getStartingDateTime( ).toLocalDate( ).equals( endingDateOfPeriodToSearch )
                                                || s.getStartingDateTime( ).toLocalDate( ).isBefore( endingDateOfPeriodToSearch ) ) )
                                .count( ) );
                        if ( nbSlots >= nbMaxAppointmentsPerUser )
                        {
                            bCheckPassed = false;
                            break;
                        }
                    }
                }
            }
        }
        return bCheckPassed;
    }

    /**
     * Check and validate all the rules for the number of booked seats asked
     * 
     * @param strNbBookedSeats
     *            the number of booked seats
     * @param form
     *            the form
     * @param nbRemainingPlaces
     *            the number of remaining places on the slot asked
     * @param locale
     *            the locale
     * @param listFormErrors
     *            the list of errors that can be fill in with the errors found for the number of booked seats
     * @return
     */
    public static int checkAndReturnNbBookedSeats( String strNbBookedSeats, AppointmentFormDTO form, AppointmentDTO appointmentDTO, Locale locale,
            List<GenericAttributeError> listFormErrors )
    {
        int nbBookedSeats = 1;
        if ( StringUtils.isEmpty( strNbBookedSeats ) && form.getMaxPeoplePerAppointment( ) > 1 )
        {
            GenericAttributeError genAttError = new GenericAttributeError( );
            genAttError.setErrorMessage( I18nService.getLocalizedString( ERROR_MESSAGE_EMPTY_NB_BOOKED_SEAT, locale ) );
            listFormErrors.add( genAttError );
        }
        if ( StringUtils.isNotEmpty( strNbBookedSeats ) )
        {
            try
            {
                nbBookedSeats = Integer.parseInt( strNbBookedSeats );
            }
            catch( NumberFormatException | NullPointerException e )
            {
                GenericAttributeError genAttError = new GenericAttributeError( );
                genAttError.setErrorMessage( I18nService.getLocalizedString( ERROR_MESSAGE_FORMAT_NB_BOOKED_SEAT, locale ) );
                listFormErrors.add( genAttError );
            }
        }
        // if it's a new appointment, need to check if the number of booked
        // seats is under or equal to the number of remaining places
        // if it's a modification, need to check if the new number of booked
        // seats is under or equal to the number of the remaining places + the
        // previous number of booked seats of the appointment
        if ( nbBookedSeats > appointmentDTO.getNbMaxPotentialBookedSeats( ) && !appointmentDTO.getOverbookingAllowed( ) )

        {
            GenericAttributeError genAttError = new GenericAttributeError( );
            genAttError.setErrorMessage( I18nService.getLocalizedString( ERROR_MESSAGE_ERROR_NB_BOOKED_SEAT, locale ) );
            listFormErrors.add( genAttError );
        }

        if ( nbBookedSeats == 0 )
        {
            GenericAttributeError genAttError = new GenericAttributeError( );
            genAttError.setErrorMessage( I18nService.getLocalizedString( ERROR_MESSAGE_EMPTY_NB_BOOKED_SEAT, locale ) );
            listFormErrors.add( genAttError );
        }
        return nbBookedSeats;
    }

    /**
     * Fill the appoinmentFront DTO with the given parameters
     * 
     * @param appointmentDTO
     *            the appointmentFront DTO
     * @param nbBookedSeats
     *            the number of booked seats
     * @param strEmail
     *            the email of the user
     * @param strFirstName
     *            the first name of the user
     * @param strLastName
     *            the last name of the user
     */
    public static void fillAppointmentDTO( AppointmentDTO appointmentDTO, int nbBookedSeats, String strEmail, String strFirstName, String strLastName )
    {
        appointmentDTO.setDateOfTheAppointment( appointmentDTO.getSlot( ).get( 0 ).getDate( ).format( Utilities.getFormatter( ) ) );
        appointmentDTO.setNbBookedSeats( nbBookedSeats );
        appointmentDTO.setEmail( strEmail );
        appointmentDTO.setFirstName( strFirstName );
        appointmentDTO.setLastName( strLastName );
    }

    /**
     * Validate the form and the additional entries of the form
     * 
     * @param appointmentDTO
     *            the appointmentFron DTo to validate
     * @param request
     *            the request
     * @param listFormErrors
     *            the list of errors that can be fill with the errors found at the validation
     */
    public static void validateFormAndEntries( AppointmentDTO appointmentDTO, HttpServletRequest request, List<GenericAttributeError> listFormErrors, boolean allEntries )
    {
        Set<ConstraintViolation<AppointmentDTO>> listErrors = BeanValidationUtil.validate( appointmentDTO );
        if ( CollectionUtils.isNotEmpty( listErrors ) )
        {
            for ( ConstraintViolation<AppointmentDTO> constraintViolation : listErrors )
            {
                GenericAttributeError genAttError = new GenericAttributeError( );
                genAttError.setErrorMessage( I18nService.getLocalizedString( constraintViolation.getMessageTemplate( ), request.getLocale( ) ) );
                listFormErrors.add( genAttError );
            }
        }
        EntryFilter filter = EntryService.buildEntryFilter( appointmentDTO.getIdForm( ) );
        if ( allEntries )
        {
            filter.setIsOnlyDisplayInBack( GenericAttributesUtils.CONSTANT_ID_NULL );
        }
        List<Entry> listEntryFirstLevel = EntryHome.getEntryList( filter );
        for ( Entry entry : listEntryFirstLevel )
        {
            listFormErrors.addAll( EntryService.getResponseEntry( request, entry.getIdEntry( ), request.getLocale( ), appointmentDTO ) );
        }
    }

    public static void fillInListResponseWithMapResponse( AppointmentDTO appointmentDTO )
    {
        Map<Integer, List<Response>> mapResponses = appointmentDTO.getMapResponsesByIdEntry( );
        if ( mapResponses != null && !mapResponses.isEmpty( ) )
        {
            List<Response> listResponse = new ArrayList<>( );
            for ( List<Response> listResponseByEntry : mapResponses.values( ) )
            {
                listResponse.addAll( listResponseByEntry );
            }
            appointmentDTO.setListResponse( listResponse );
        }
    }

    /**
     * Build a list of response of the appointment
     * 
     * @param appointment
     *            the appointment
     * @param request
     *            the request
     * @param locale
     *            the local
     * @return a list of response
     */
    public static List<ResponseRecapDTO> buildListResponse( AppointmentDTO appointment, HttpServletRequest request, Locale locale )
    {
        List<ResponseRecapDTO> listResponseRecapDTO = new ArrayList<>( );
        HashMap<Integer, List<ResponseRecapDTO>> mapResponse = new HashMap<>( );
        if ( CollectionUtils.isNotEmpty( appointment.getListResponse( ) ) )
        {
            listResponseRecapDTO = new ArrayList<>( appointment.getListResponse( ).size( ) );
            for ( Response response : appointment.getListResponse( ) )
            {
                int nIndex = response.getEntry( ).getPosition( );
                IEntryTypeService entryTypeService = EntryTypeServiceManager.getEntryTypeService( response.getEntry( ) );
                ResponseRecapDTO responseRecapDTO = new ResponseRecapDTO( response,
                        entryTypeService.getResponseValueForRecap( response.getEntry( ), request, response, locale ) );
                
                
                List<ResponseRecapDTO> listResponse = mapResponse.computeIfAbsent( nIndex, ArrayList::new );
                listResponse.add( responseRecapDTO );
            }
        }
        for ( List<ResponseRecapDTO> listResponse : mapResponse.values( ) )
        {
            listResponseRecapDTO.addAll( listResponse );
        }
        return listResponseRecapDTO;
    }

    /**
     * Kill the lock timer on a slot
     * 
     * @param request
     *            the request
     */
    public static void killTimer( HttpServletRequest request, int idSlot )
    {
        TimerForLockOnSlot timer = (TimerForLockOnSlot) request.getSession( ).getAttribute( SESSION_TIMER_SLOT + idSlot );
        if ( timer != null )
        {
            timer.setIsCancelled( true );
            timer.cancel( );
            request.getSession( ).removeAttribute( SESSION_TIMER_SLOT + idSlot );
        }
    }

    /**
     * Create a timer on a slot
     * 
     * @param slot
     *            the slot
     * @param appointmentDTO
     *            the appointment
     * @param maxPeoplePerAppointment
     *            the max people per appointment
     * @return the timer
     */
    public static synchronized Timer putTimerInSession( HttpServletRequest request, int nIdSlot, AppointmentDTO appointmentDTO, int maxPeoplePerAppointment )
    {
        Slot slot = SlotService.findSlotById( nIdSlot );

        int nbPotentialRemainingPlaces = slot.getNbPotentialRemainingPlaces( );
        int nbPotentialPlacesTaken = Math.min( nbPotentialRemainingPlaces, maxPeoplePerAppointment );
        int nNewNbMaxPotentialBookedSeats = Math.min( nbPotentialPlacesTaken + appointmentDTO.getNbMaxPotentialBookedSeats( ), maxPeoplePerAppointment );

        if ( slot.getNbPotentialRemainingPlaces( ) > 0 )
        {

            appointmentDTO.setNbMaxPotentialBookedSeats( nNewNbMaxPotentialBookedSeats );
            SlotSafeService.decrementPotentialRemainingPlaces( nbPotentialPlacesTaken, slot.getIdSlot( ) );

            TimerForLockOnSlot timer = new TimerForLockOnSlot( );
            SlotEditTask slotEditTask = new SlotEditTask( timer );
            slotEditTask.setNbPlacesTaken( nbPotentialPlacesTaken );
            slotEditTask.setIdSlot( slot.getIdSlot( ) );
            long delay = TimeUnit.MINUTES.toMillis( AppPropertiesService.getPropertyInt( PROPERTY_DEFAULT_EXPIRED_TIME_EDIT_APPOINTMENT, 1 ) );
            timer.schedule( slotEditTask, delay );
            request.getSession( ).setAttribute( AppointmentUtilities.SESSION_TIMER_SLOT + slotEditTask.getIdSlot( ), timer );
            return timer;
        }
        appointmentDTO.setNbMaxPotentialBookedSeats( 0 );
        return null;
    }

    /**
     * Get Form Permissions
     * 
     * @param listForms
     * @param request
     * @return
     */
    public static String [ ] [ ] getPermissions( List<AppointmentFormDTO> listForms, AdminUser user )
    {
        String [ ] [ ] retour = new String [ listForms.size( )] [ 6];
        int nI = 0;

        for ( AppointmentFormDTO tmpForm : listForms )
        {
            
            String [ ] strRetour = new String [ 7];
            strRetour [0] = String.valueOf( RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, String.valueOf( tmpForm.getIdForm( ) ),
                    AppointmentResourceIdService.PERMISSION_VIEW_APPOINTMENT, (fr.paris.lutece.api.user.User) user ) );
            strRetour [1] = String.valueOf( RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, String.valueOf( tmpForm.getIdForm( ) ),
                    AppointmentResourceIdService.PERMISSION_MODIFY_ADVANCED_SETTING_FORM, (fr.paris.lutece.api.user.User) user ) );
            strRetour [2] = String.valueOf( RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, String.valueOf( tmpForm.getIdForm( ) ),
                    AppointmentResourceIdService.PERMISSION_MODIFY_FORM, (fr.paris.lutece.api.user.User) user ) );
            strRetour [3] = String.valueOf( RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, String.valueOf( tmpForm.getIdForm( ) ),
                    AppointmentResourceIdService.PERMISSION_MODIFY_FORM, (fr.paris.lutece.api.user.User) user ) );
            strRetour [4] = String.valueOf( RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, String.valueOf( tmpForm.getIdForm( ) ),
                    AppointmentResourceIdService.PERMISSION_CHANGE_STATE, (fr.paris.lutece.api.user.User) user ) );
            strRetour [5] = String.valueOf( RBACService.isAuthorized( AppointmentFormDTO.RESOURCE_TYPE, String.valueOf( tmpForm.getIdForm( ) ),
                    AppointmentResourceIdService.PERMISSION_DELETE_FORM, (fr.paris.lutece.api.user.User) user ) );
            retour [nI++] = strRetour;
        }

        return retour;
    }

    /**
     * Return the min starting time to display
     * 
     * @param minStartingTime
     *            the min starting time
     * @return 00 if the minstarting time is under 30, 30 otherwise
     */
    public static LocalTime getMinTimeToDisplay( LocalTime minStartingTime )
    {
        LocalTime minStartingTimeToDisplay;
        if ( minStartingTime.getMinute( ) < THIRTY_MINUTES )
        {
            minStartingTimeToDisplay = LocalTime.of( minStartingTime.getHour( ), 0 );
        }
        else
        {
            minStartingTimeToDisplay = LocalTime.of( minStartingTime.getHour( ), THIRTY_MINUTES );
        }
        return minStartingTimeToDisplay;
    }

    /**
     * Return the max ending time to display
     * 
     * @param maxEndingTime
     *            the max ending time
     * @return 30 if the max ending time is under 30, otherwise the next hour
     */
    public static LocalTime getMaxTimeToDisplay( LocalTime maxEndingTime )
    {
        LocalTime maxEndingTimeToDisplay;
        if ( maxEndingTime.getMinute( ) < THIRTY_MINUTES )
        {
            maxEndingTimeToDisplay = LocalTime.of( maxEndingTime.getHour( ), THIRTY_MINUTES );
        }
        else
        {
            maxEndingTimeToDisplay = LocalTime.of( maxEndingTime.getHour( ) + 1, 0 );
        }
        return maxEndingTimeToDisplay;
    }

    /**
     * Check if there are appointments impacted by the new week definition
     * 
     * @param listAppointment
     *            the list of appointments
     * @param nIdForm
     *            the form Id
     * @param dateOfModification
     *            the date of modification (date of apply of the new week definition)
     * @param appointmentForm
     *            the appointment form
     * @return true if there are appointments impacted
     */
    public static boolean checkNoAppointmentsImpacted( List<Appointment> listAppointment, int nIdForm, LocalDate dateOfModification,
            AppointmentFormDTO appointmentForm )
    {
    	ReservationRule previousReservationRule = ReservationRuleService.findReservationRuleByIdFormAndClosestToDateOfApply( nIdForm, dateOfModification );
    	return checkNoAppointmentsImpacted (listAppointment, nIdForm,previousReservationRule.getIdReservationRule( ), appointmentForm );
    }
    /**
     * Check if there are appointments impacted by the new week definition
     * 
     * @param listAppointment
     *            the list of appointments
     * @param nIdForm
     *            the form Id
     * @param nIdreservationRule
     *            the reservationRule id
     * @param appointmentForm
     *            the appointment form
     * @return true if there are appointments impacted
     */
    public static boolean checkNoAppointmentsImpacted( List<Appointment> listAppointment, int nIdForm,int nIdreservationRule,
            AppointmentFormDTO appointmentForm )
    {
        boolean bNoAppointmentsImpacted = true;
        // Build the previous appointment form with the previous week
        // definition and the previous reservation rule
        AppointmentFormDTO previousAppointmentForm = FormService.buildAppointmentForm( nIdForm, nIdreservationRule );
        // Need to check if the new definition week has more open days.
        List<DayOfWeek> previousOpenDays = WorkingDayService.getOpenDays( previousAppointmentForm );
        List<DayOfWeek> newOpenDays = WorkingDayService.getOpenDays( appointmentForm );
        // If new open days
        if ( newOpenDays.containsAll( previousOpenDays ) )
        {
            // Nothing to check
        }
        else
        {
            // Else we remove all the corresponding days
            previousOpenDays.removeAll( newOpenDays );
            // For the remaining days
            // for each appointment, need to check if the appointment is
            // not in the remaining open days
            boolean bAppointmentOnOpenDays = false;
            for ( Appointment appointment : listAppointment )
            {
                for ( AppointmentSlot appSlot : appointment.getListAppointmentSlot( ) )
                {

                    Slot tempSlot = SlotService.findSlotById( appSlot.getIdSlot( ) );
                    if ( previousOpenDays.contains( tempSlot.getStartingDateTime( ).getDayOfWeek( ) ) )
                    {
                        bAppointmentOnOpenDays = true;
                        break;
                    }
                }
                if ( bAppointmentOnOpenDays )
                {
                    break;
                }
            }
            bNoAppointmentsImpacted = !bAppointmentOnOpenDays;
        }
        LocalTime newStartingTime = LocalTime.parse( appointmentForm.getTimeStart( ) );
        LocalTime newEndingTime = LocalTime.parse( appointmentForm.getTimeEnd( ) );
        LocalTime oldStartingTime = LocalTime.parse( previousAppointmentForm.getTimeStart( ) );
        LocalTime oldEndingTime = LocalTime.parse( previousAppointmentForm.getTimeEnd( ) );
        // If we have changed the duration of an appointment
        if ( appointmentForm.getDurationAppointments( ) != previousAppointmentForm.getDurationAppointments( ) )
        {
            bNoAppointmentsImpacted = false;
        }
        // If we have change the open hours
    
        if ( !newStartingTime.equals( oldStartingTime )  || !newEndingTime.equals( oldEndingTime )  )
        {
            bNoAppointmentsImpacted = false;
        }

        return bNoAppointmentsImpacted;
    }
    
    /**
     * Check if there are appointments impacted by the new week definition
     * @param listSlotsImpacted the list of slot impacted
     * @param newReservationRule the reservation rule
     * @return true if there are no appointments impacted
     */
    public static boolean checkNoAppointmentsImpacted( List<Slot> listSlotsImpacted, int idReservationRule  )
    {
    	
    	  List<WorkingDay> listWorkingDay=  WorkingDayService.findListWorkingDayByWeekDefinitionRule( idReservationRule );
          for ( Slot slot : listSlotsImpacted )
          {        	  
        	  WorkingDay workingDay= listWorkingDay.stream().filter(day -> day.getDayOfWeek() == slot.getDate().getDayOfWeek().getValue( ) ).findFirst().orElse(null);
        	  if (workingDay != null ) {
        		  
        		 if( workingDay.getListTimeSlot().stream().noneMatch(  time -> slot.getStartingTime().equals(time.getStartingTime()) && slot.getEndingTime().equals(time.getEndingTime()))){
        	  		  
        		   return false;
        		 }
        	  
        	  }else {
        		  
        		  return false;
        	  }         	 
          }

       
        return true;
    }
   
    /**
     * Check that there is no validated appointments on a slot
     * 
     * @param slot
     *            the slot
     * @return true if there are no validated appointments on this slot, false otherwise
     */
    public static boolean checkNoValidatedAppointmentsOnThisSlot( Slot slot )
    {
        boolean bNoValidatedAppointmentsOnThisSlot = true;
        List<Appointment> listAppointmentsOnThisSlot = AppointmentService.findListAppointmentBySlot( slot.getIdSlot( ) );
        if ( CollectionUtils.isNotEmpty( listAppointmentsOnThisSlot ) )
        {
            listAppointmentsOnThisSlot = listAppointmentsOnThisSlot.stream( ).filter( a -> !a.getIsCancelled( ) ).collect( Collectors.toList( ) );
        }
        if ( CollectionUtils.isNotEmpty( listAppointmentsOnThisSlot ) )
        {
            bNoValidatedAppointmentsOnThisSlot = false;
        }
        return bNoValidatedAppointmentsOnThisSlot;
    }

    /**
     * Return the slots impacted by the modification of this time slot
     * 
     * @param timeSlot
     *            the time slot
     * @param nIdForm
     *            the form id
     * @param nIdWeekDefinition
     *            the week definition id
     * @param bShiftSlot
     *            the boolean value for the shift
     * @return the list of slots impacted
     */
    public static List<Slot> findSlotsImpactedByThisTimeSlot( TimeSlot timeSlot, int nIdForm, int nIdWeekDefinition, boolean bShiftSlot )
    {
        List<Slot> listSlotsImpacted = new ArrayList<>( );
        LocalDate maxDate = null;
        // Get the weekDefinition that is currently modified
        WeekDefinition currentModifiedWeekDefinition = WeekDefinitionService.findWeekDefinitionById( nIdWeekDefinition );
        // Find the next weekDefinition, if exist, to have the max date to
        // search slots with appointments
        WeekDefinition nextWeekDefinition = WeekDefinitionService.findNextWeekDefinition( nIdForm, currentModifiedWeekDefinition.getDateOfApply( ) );
        if ( nextWeekDefinition != null )
        {
            maxDate = nextWeekDefinition.getDateOfApply( );
        }
        else
        {
            // If there is no next weekDefinition
            // Get the ending validity date of the form
            Form form = FormService.findFormLightByPrimaryKey( nIdForm );
            if ( form.getEndingValidityDate( ) != null )
            {
                maxDate = form.getEndingValidityDate( );
            }
            else
            {
                // If there is no ending validity date
                // Find the slot with the max date
                Slot slotWithMaxDate = SlotService.findSlotWithMaxDate( nIdForm );
                if ( slotWithMaxDate != null && slotWithMaxDate.getStartingDateTime( ) != null )
                {
                    maxDate = slotWithMaxDate.getStartingDateTime( ).toLocalDate( );
                }
            }
        }
        if ( maxDate != null )
        {
            // We have an upper bound to search with
            List<Slot> listSlots = SlotService.findSlotsByIdFormAndDateRange( nIdForm, currentModifiedWeekDefinition.getDateOfApply( ).atStartOfDay( ),
                    maxDate.atTime( LocalTime.MAX ) );
            // Need to check if the modification of the time slot or the typical
            // week impacts these slots
            WorkingDay workingDay = WorkingDayService.findWorkingDayLightById( timeSlot.getIdWorkingDay( ) );
            // Filter all the slots with the working day and the starting time
            // ending time of the time slot
            // The begin time of the slot can be before or after the begin time
            // of the time slot
            // and the ending time of the slot can be before or after the ending
            // time of the time slot (specific slot)

            // If shiftTimeSlot is checked, need to check all the slots impacted
            // until the end of the day
            if ( bShiftSlot )
            {
                listSlotsImpacted = listSlots.stream( )
                        .filter( slot -> ( ( slot.getStartingDateTime( ).getDayOfWeek( ) == DayOfWeek.of( workingDay.getDayOfWeek( ) ) )
                                && ( !slot.getStartingTime( ).isBefore( timeSlot.getStartingTime( ) )
                                        || ( slot.getStartingTime( ).isBefore( timeSlot.getStartingTime( ) )
                                                && ( slot.getEndingTime( ).isAfter( timeSlot.getStartingTime( ) ) ) ) ) ) )
                        .collect( Collectors.toList( ) );
            }
            else
            {
                listSlotsImpacted = listSlots.stream( )
                        .filter( slot -> ( slot.getStartingDateTime( ).getDayOfWeek( ) == DayOfWeek.of( workingDay.getDayOfWeek( ) ) )
                                && ( slot.getStartingTime( ).equals( timeSlot.getStartingTime( ) )
                                        || ( slot.getStartingTime( ).isBefore( timeSlot.getStartingTime( ) )
                                                && ( slot.getEndingTime( ).isAfter( timeSlot.getStartingTime( ) ) ) )
                                        || ( slot.getStartingTime( ).isAfter( timeSlot.getStartingTime( ) )
                                                && ( !slot.getEndingTime( ).isAfter( timeSlot.getEndingTime( ) ) ) ) ) )
                        .collect( Collectors.toList( ) );
            }
        }
        return listSlotsImpacted;
    }

    public static LocalDateTime getStartingDateTime( Appointment appointmentDTO )
    {

        List<Slot> listSlot = appointmentDTO.getSlot( );
        if ( CollectionUtils.isNotEmpty( listSlot ) )
        {
            Slot slot = listSlot.stream( ).min( Comparator.comparing( Slot::getStartingDateTime ) ).orElse( listSlot.get( 0 ) );
            return slot.getStartingDateTime( );
        }

        return null;
    }

    public static LocalDateTime getEndingDateTime( Appointment appointmentDTO )
    {

        List<Slot> listSlot = appointmentDTO.getSlot( );
        if ( listSlot != null && !listSlot.isEmpty( ) )
        {

            Slot slot = listSlot.stream( ).max( Comparator.comparing( Slot::getStartingDateTime ) ).orElse( listSlot.get( 0 ) );
            return slot.getEndingDateTime( );
        }

        return null;
    }

    /**
     * return true if all slots are consecutive
     * 
     * @param allSlots
     *            the list of slots
     * @return true if all slots are consecutive
     */
    public static boolean isConsecutiveSlots( List<Slot> allSlots )
    {
        Slot slot = null;
        for ( Slot nextSlot : allSlots )
        {
            if ( nextSlot == null || ( slot != null && !Objects.equals( slot.getEndingDateTime( ), nextSlot.getStartingDateTime( ) ) ) )
            {
                return false;
            }
            slot = nextSlot;
        }
        return true;
    }
}
