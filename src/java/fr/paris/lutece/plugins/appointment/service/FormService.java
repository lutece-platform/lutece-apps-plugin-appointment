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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;

import fr.paris.lutece.plugins.appointment.business.appointment.Appointment;
import fr.paris.lutece.plugins.appointment.business.display.Display;
import fr.paris.lutece.plugins.appointment.business.form.Form;
import fr.paris.lutece.plugins.appointment.business.form.FormHome;
import fr.paris.lutece.plugins.appointment.business.localization.Localization;
import fr.paris.lutece.plugins.appointment.business.message.FormMessage;
import fr.paris.lutece.plugins.appointment.business.planning.ClosingDay;
import fr.paris.lutece.plugins.appointment.business.planning.TimeSlot;
import fr.paris.lutece.plugins.appointment.business.planning.WeekDefinition;
import fr.paris.lutece.plugins.appointment.business.planning.WorkingDay;
import fr.paris.lutece.plugins.appointment.business.rule.FormRule;
import fr.paris.lutece.plugins.appointment.business.rule.ReservationRule;
import fr.paris.lutece.plugins.appointment.business.slot.Slot;
import fr.paris.lutece.plugins.appointment.service.listeners.AppointmentListenerManager;
import fr.paris.lutece.plugins.appointment.service.listeners.FormListenerManager;
import fr.paris.lutece.plugins.appointment.web.dto.AppointmentFormDTO;
import fr.paris.lutece.plugins.genericattributes.business.Entry;
import fr.paris.lutece.plugins.genericattributes.business.EntryFilter;
import fr.paris.lutece.plugins.genericattributes.business.EntryHome;
import fr.paris.lutece.util.ReferenceList;

/**
 * Service class for a form
 * 
 * @author Laurent Payen
 *
 */
public final class FormService
{

    /**
     * Private constructor - this class does not need to be instantiated
     */
    private FormService( )
    {
    }

    /**
     * Make a copy of form, with all its values
     * 
     * @param nIdForm
     *            the Form Id to copy
     * @param newNameForCopy
     *            the new Name of the copy
     * @return the id of the form created
     */
    public static int copyForm( int nIdForm, String newNameForCopy )
    {
        // Build the simple form to copy with the values of the original form
        AppointmentFormDTO appointmentForm = buildAppointmentForm( nIdForm, 0, 0 );
        appointmentForm.setTitle( newNameForCopy );
        appointmentForm.setIsActive( Boolean.FALSE );
        appointmentForm.setDateStartValidity( null );
        appointmentForm.setDateEndValidity( null );
        // Save it
        Form form = FormService.createForm( appointmentForm );
        int nIdNewForm = form.getIdForm( );
        // Add the display
        DisplayService.createDisplay( appointmentForm, nIdNewForm );
        // Add the localization
        LocalizationService.createLocalization( appointmentForm, nIdNewForm );
        // Add the form rule
        FormRuleService.createFormRule( appointmentForm, nIdNewForm );
        // Get all the weekDefinitions, WorkingDays and TimeSlots of the
        // original form and set the new id
        // of the copy of the form and save them
        WeekDefinition copyWeekDefinition;
        int idCopyWeekDefinition;
        WorkingDay copyWorkingDay;
        int idCopyWorkingDay;
        TimeSlot copyTimeSlot;
        List<WeekDefinition> listWeekDefinitions = WeekDefinitionService.findListWeekDefinition( nIdForm );
        List<WorkingDay> listWorkingDays;
        List<TimeSlot> listTimeSlots;
        for ( WeekDefinition weekDefinition : listWeekDefinitions )
        {
            copyWeekDefinition = weekDefinition;
            copyWeekDefinition.setIdWeekDefinition( 0 );
            copyWeekDefinition.setIdForm( nIdNewForm );
            copyWeekDefinition = WeekDefinitionService.saveWeekDefinition( copyWeekDefinition );
            listWorkingDays = weekDefinition.getListWorkingDay( );
            idCopyWeekDefinition = copyWeekDefinition.getIdWeekDefinition( );
            for ( WorkingDay workingDay : listWorkingDays )
            {
                copyWorkingDay = workingDay;
                copyWorkingDay.setIdWorkingDay( 0 );
                copyWorkingDay.setIdWeekDefinition( idCopyWeekDefinition );
                copyWorkingDay = WorkingDayService.saveWorkingDay( copyWorkingDay );
                idCopyWorkingDay = copyWorkingDay.getIdWorkingDay( );
                listTimeSlots = workingDay.getListTimeSlot( );
                for ( TimeSlot timeSlot : listTimeSlots )
                {
                    copyTimeSlot = timeSlot;
                    copyTimeSlot.setIdTimeSlot( 0 );
                    copyTimeSlot.setIdWorkingDay( idCopyWorkingDay );
                    TimeSlotService.saveTimeSlot( copyTimeSlot );
                }
            }
        }
        // Get all the reservation rules of the original form and set the new id
        // of the copy of the form and save them
        ReservationRule copyReservationRule;
        List<ReservationRule> listReservationRules = ReservationRuleService.findListReservationRule( nIdForm );
        for ( ReservationRule reservationRule : listReservationRules )
        {
            copyReservationRule = reservationRule;
            copyReservationRule.setIdReservationRule( 0 );
            copyReservationRule.setIdForm( nIdNewForm );
            ReservationRuleService.saveReservationRule( copyReservationRule );
        }
        // Copy the messages of the original form and add them to the copy
        FormMessage formMessage = FormMessageService.findFormMessageByIdForm( nIdForm );
        FormMessage copyFormMessage = formMessage;
        copyFormMessage.setIdFormMessage( 0 );
        copyFormMessage.setIdForm( nIdNewForm );
        FormMessageService.saveFormMessage( copyFormMessage );
        // Get all the closing days of the original form and add them to the
        // copy
        List<ClosingDay> listClosingDays = ClosingDayService.findListClosingDay( nIdForm );
        ClosingDay copyClosingDay;
        for ( ClosingDay closingDay : listClosingDays )
        {
            copyClosingDay = closingDay;
            copyClosingDay.setIdClosingDay( 0 );
            copyClosingDay.setIdForm( nIdNewForm );
            ClosingDayService.saveClosingDay( copyClosingDay );
        }
        // Get all the specific slots of the original form and copy them for the
        // new form
        List<Slot> listSpecificSlots = SlotService.findSpecificSlotsByIdForm( nIdForm );
        Slot copySpecificSlot;
        for ( Slot specificSlot : listSpecificSlots )
        {
            copySpecificSlot = specificSlot;
            copySpecificSlot.setIdSlot( 0 );
            copySpecificSlot.setIdForm( nIdNewForm );
            SlotService.saveSlot( copySpecificSlot );
        }
        // Copy the entries of the original form
        EntryFilter entryFilter = new EntryFilter( );
        entryFilter.setIdResource( nIdForm );
        entryFilter.setResourceType( AppointmentFormDTO.RESOURCE_TYPE );
        entryFilter.setEntryParentNull( EntryFilter.FILTER_TRUE );
        entryFilter.setFieldDependNull( EntryFilter.FILTER_TRUE );
        List<Entry> listEntries = EntryHome.getEntryList( entryFilter );
        for ( Entry entry : listEntries )
        {
            entry.setIdResource( nIdNewForm );
            EntryHome.copy( entry );
        }
        return nIdNewForm;
    }

    /**
     * Save a form in database
     * 
     * @param form
     *            the form to save
     * @return the form saved (with its id)
     */
    public static Form saveForm( Form form )
    {
        return FormHome.create( form );
    }

    /**
     * Create a form from an appointmentForm DTO
     * 
     * @param appointmentForm
     *            the appointmentForm DTO
     * @return the id of the form created
     */
    public static int createAppointmentForm( AppointmentFormDTO appointmentForm )
    {
        Form form = FormService.createForm( appointmentForm );
        int nIdForm = form.getIdForm( );
        FormMessageService.createFormMessageWithDefaultValues( nIdForm );
        LocalDate dateNow = LocalDate.now( );
        DisplayService.createDisplay( appointmentForm, nIdForm );
        LocalizationService.createLocalization( appointmentForm, nIdForm );
        FormRuleService.createFormRule( appointmentForm, nIdForm );
        ReservationRule reservationRule = ReservationRuleService.createReservationRule( appointmentForm, nIdForm, dateNow );
        int nMaxCapacity = reservationRule.getMaxCapacityPerSlot( );
        WeekDefinition weekDefinition = WeekDefinitionService.createWeekDefinition( nIdForm, dateNow );
        int nIdWeekDefinition = weekDefinition.getIdWeekDefinition( );
        LocalTime startingTime = LocalTime.parse( appointmentForm.getTimeStart( ) );
        LocalTime endingTime = LocalTime.parse( appointmentForm.getTimeEnd( ) );
        int nDuration = appointmentForm.getDurationAppointments( );
        for ( DayOfWeek dayOfWeek : WorkingDayService.getOpenDays( appointmentForm ) )
        {
            WorkingDayService.generateWorkingDayAndListTimeSlot( nIdWeekDefinition, dayOfWeek, startingTime, endingTime, nDuration, nMaxCapacity );
        }
        return nIdForm;
    }

    /**
     * Update a form with the new values of the Global Parameters of an appointmentForm DTO
     * 
     * @param appointmentForm
     *            the appointmentForm DTO
     * 
     */
    public static void updateGlobalParameters( AppointmentFormDTO appointmentForm )
    {
        Form form = FormService.updateForm( appointmentForm );
        int nIdForm = form.getIdForm( );
        DisplayService.updateDisplay( appointmentForm, nIdForm );
        LocalizationService.updateLocalization( appointmentForm, nIdForm );
        FormRuleService.updateFormRule( appointmentForm, nIdForm );
    }

    /**
     * Update a form with the new values of an appointmentForm DTO Advanced Parameters (with a date of application) --> new Typical Week
     * 
     * @param appointmentForm
     *            the appointmentForm DTO
     * @param dateOfModification
     *            the date of the update
     */
    public static void updateAdvancedParameters( AppointmentFormDTO appointmentForm, LocalDate dateOfModification )
    {
        int nIdForm = appointmentForm.getIdForm( );
        ReservationRule reservationRule = ReservationRuleService.updateReservationRule( appointmentForm, nIdForm, dateOfModification );
        int nMaxCapacity = reservationRule.getMaxCapacityPerSlot( );
        WeekDefinition weekDefinition = WeekDefinitionService.updateWeekDefinition( nIdForm, dateOfModification );
        int nIdWeekDefinition = weekDefinition.getIdWeekDefinition( );
        List<WorkingDay> listWorkingDay = WorkingDayService.findListWorkingDayByWeekDefinition( nIdWeekDefinition );
        if ( CollectionUtils.isNotEmpty( listWorkingDay ) )
        {
            WorkingDayService.deleteListWorkingDay( listWorkingDay );
        }
        LocalTime startingHour = LocalTime.parse( appointmentForm.getTimeStart( ) );
        LocalTime endingHour = LocalTime.parse( appointmentForm.getTimeEnd( ) );
        int nDuration = appointmentForm.getDurationAppointments( );
        for ( DayOfWeek dayOfWeek : WorkingDayService.getOpenDays( appointmentForm ) )
        {
            WorkingDayService.generateWorkingDayAndListTimeSlot( nIdWeekDefinition, dayOfWeek, startingHour, endingHour, nDuration, nMaxCapacity );
        }
    }

    /**
     * Build all the active forms
     * 
     * @return the list of appointment form dto that are active
     */
    public static List<AppointmentFormDTO> buildAllActiveAppointmentForm( )
    {
        List<AppointmentFormDTO> listActiveAppointmentForm = new ArrayList<>( );
        for ( Form form : FormHome.findActiveForms( ) )
        {
            listActiveAppointmentForm.add( buildAppointmentForm( form.getIdForm( ), 0, 0 ) );
        }
        return listActiveAppointmentForm;
    }

    /**
     * Build all the appointForm DTO of the database light because the appointFormDTO is only fill in with the form id, the title and if the form is active or
     * not
     * 
     * @return the list of all the appointmentForm DTO
     */
    public static List<AppointmentFormDTO> buildAllAppointmentFormLight( )
    {
        List<AppointmentFormDTO> listAppointmentFormLight = new ArrayList<>( );
        for ( Form form : FormService.findAllForms( ) )
        {
            checkValidityDate( form );
            listAppointmentFormLight.add( buildAppointmentFormLight( form ) );
        }
        return listAppointmentFormLight;
    }

    /**
     * Build a list of all the forms (id, title)
     * 
     * @return the reference list
     */
    public static ReferenceList findAllInReferenceList( )
    {
        List<Form> listForm = findAllForms( );
        ReferenceList refListForms = new ReferenceList( listForm.size( ) );
        for ( Form form : listForm )
        {
            refListForms.addItem( form.getIdForm( ), form.getTitle( ) );
        }
        return refListForms;
    }

    /**
     * Build all the active forms of the database
     * 
     * @return a list of appointmentForm DTO
     */
    public static List<AppointmentFormDTO> buildAllActiveAndDisplayedOnPortletAppointmentForm( )
    {
        List<AppointmentFormDTO> listAppointmentForm = new ArrayList<>( );
        for ( Form form : FormService.findAllActiveAndDisplayedOnPortletForms( ) )
        {
            listAppointmentForm.add( buildAppointmentForm( form.getIdForm( ), 0, 0 ) );
        }
        return listAppointmentForm;
    }

    /**
     * Build an appointmentFormDTO light
     * 
     * @param form
     *            the form object
     * @return the appointmentForm DTO
     */
    public static AppointmentFormDTO buildAppointmentFormLight( Form form )
    {
        AppointmentFormDTO appointmentForm = new AppointmentFormDTO( );
        if ( form != null )
        {
            fillAppointmentFormWithFormPart( appointmentForm, form );
        }
        return appointmentForm;
    }

    /**
     * Build an appointmentForm light
     * 
     * @param nIdForm
     *            the form Id
     * @return the appointmentForm DTO
     */
    public static AppointmentFormDTO buildAppointmentFormLight( int nIdForm )
    {
        Form form = FormService.findFormLightByPrimaryKey( nIdForm );
        return buildAppointmentFormLight( form );
    }

    /**
     * Build an appointmentForm DTO
     * 
     * @param nIdForm
     *            the Form Id
     * @param nIdReservationRule
     *            the Reservation Rule Id
     * @param nIdWeekDefinition
     *            the WeekDefinition Id
     * @return the apointmentForm DTO built
     */
    public static AppointmentFormDTO buildAppointmentForm( int nIdForm, int nIdReservationRule, int nIdWeekDefinition )
    {
        AppointmentFormDTO appointmentForm = new AppointmentFormDTO( );
        Form form = FormService.findFormLightByPrimaryKey( nIdForm );
        fillAppointmentFormWithFormPart( appointmentForm, form );
        Display display = DisplayService.findDisplayWithFormId( nIdForm );
        if ( display != null )
        {
            fillAppointmentFormWithDisplayPart( appointmentForm, display );
        }
        Localization localization = LocalizationService.findLocalizationWithFormId( nIdForm );
        if ( localization != null )
        {
            fillAppointmentFormWithLocalizationPart( appointmentForm, localization );
        }
        FormRule formRule = FormRuleService.findFormRuleWithFormId( nIdForm );
        if ( formRule != null )
        {
            fillAppointmentFormWithFormRulePart( appointmentForm, formRule );
        }
        ReservationRule reservationRule = null;
        WeekDefinition weekDefinition = null;
        LocalDate dateOfApply = LocalDate.now( );
        if ( nIdReservationRule > 0 )
        {
            reservationRule = ReservationRuleService.findReservationRuleById( nIdReservationRule );
            dateOfApply = reservationRule.getDateOfApply( );
        }
        if ( nIdWeekDefinition > 0 )
        {
            weekDefinition = WeekDefinitionService.findWeekDefinitionById( nIdWeekDefinition );
            dateOfApply = weekDefinition.getDateOfApply( );
        }
        if ( reservationRule == null )
        {
            reservationRule = ReservationRuleService.findReservationRuleByIdFormAndClosestToDateOfApply( nIdForm, dateOfApply );
        }
        if ( weekDefinition == null )
        {
            weekDefinition = WeekDefinitionService.findWeekDefinitionByIdFormAndClosestToDateOfApply( nIdForm, dateOfApply );
        }
        if ( reservationRule != null )
        {
            fillAppointmentFormWithReservationRulePart( appointmentForm, reservationRule );
        }
        if ( weekDefinition != null )
        {
            fillAppointmentFormWithWeekDefinitionPart( appointmentForm, weekDefinition );
        }
        return appointmentForm;
    }

    /**
     * Fill the appointmentForm DTO with the WeekDefinition
     * 
     * @param appointmentForm
     *            the appointmentForm DTO
     * @param weekDefinition
     *            the week definition
     */
    private static void fillAppointmentFormWithWeekDefinitionPart( AppointmentFormDTO appointmentForm, WeekDefinition weekDefinition )
    {
        List<WorkingDay> listWorkingDay = weekDefinition.getListWorkingDay( );
        if ( CollectionUtils.isNotEmpty( listWorkingDay ) )
        {
            for ( WorkingDay workingDay : listWorkingDay )
            {
                DayOfWeek dayOfWeek = DayOfWeek.of( workingDay.getDayOfWeek( ) );
                switch( dayOfWeek )
                {
                    case MONDAY:
                        appointmentForm.setIsOpenMonday( Boolean.TRUE );
                        break;
                    case TUESDAY:
                        appointmentForm.setIsOpenTuesday( Boolean.TRUE );
                        break;
                    case WEDNESDAY:
                        appointmentForm.setIsOpenWednesday( Boolean.TRUE );
                        break;
                    case THURSDAY:
                        appointmentForm.setIsOpenThursday( Boolean.TRUE );
                        break;
                    case FRIDAY:
                        appointmentForm.setIsOpenFriday( Boolean.TRUE );
                        break;
                    case SATURDAY:
                        appointmentForm.setIsOpenSaturday( Boolean.TRUE );
                        break;
                    case SUNDAY:
                        appointmentForm.setIsOpenSunday( Boolean.TRUE );
                        break;
                }
            }
            // We suppose that all the days have the same opening and closing
            // hours (it can be modified after)
            LocalTime minStartingTime = WorkingDayService.getMinStartingTimeOfAListOfWorkingDay( listWorkingDay );
            LocalTime maxEndingTime = WorkingDayService.getMaxEndingTimeOfAListOfWorkingDay( listWorkingDay );
            int nDurationAppointment = WorkingDayService.getMinDurationTimeSlotOfAListOfWorkingDay( listWorkingDay );
            appointmentForm.setTimeStart( minStartingTime.toString( ) );
            appointmentForm.setTimeEnd( maxEndingTime.toString( ) );
            appointmentForm.setDurationAppointments( nDurationAppointment );
        }
    }

    /**
     * Fill the appointmentForm DTO with the Reservation Rule
     * 
     * @param appointmentForm
     *            the appointmentForm DTO
     * @param reservationRule
     *            the reservation rule
     */
    private static void fillAppointmentFormWithReservationRulePart( AppointmentFormDTO appointmentForm, ReservationRule reservationRule )
    {
        appointmentForm.setIdReservationRule( reservationRule.getIdReservationRule( ) );
        appointmentForm.setMaxCapacityPerSlot( reservationRule.getMaxCapacityPerSlot( ) );
        appointmentForm.setMaxPeoplePerAppointment( reservationRule.getMaxPeoplePerAppointment( ) );
    }

    /**
     * Fill the appointmentForm DTO with the form rule
     * 
     * @param appointmentForm
     *            the appointmentForm DTO
     * @param formRule
     *            the form rule
     */
    private static void fillAppointmentFormWithFormRulePart( AppointmentFormDTO appointmentForm, FormRule formRule )
    {
        appointmentForm.setEnableCaptcha( formRule.getIsCaptchaEnabled( ) );
        appointmentForm.setEnableMandatoryEmail( formRule.getIsMandatoryEmailEnabled( ) );
        appointmentForm.setActiveAuthentication( formRule.getIsActiveAuthentication( ) );
        appointmentForm.setNbDaysBeforeNewAppointment( formRule.getNbDaysBeforeNewAppointment( ) );
        appointmentForm.setMinTimeBeforeAppointment( formRule.getMinTimeBeforeAppointment( ) );
        appointmentForm.setNbMaxAppointmentsPerUser( formRule.getNbMaxAppointmentsPerUser( ) );
        appointmentForm.setNbDaysForMaxAppointmentsPerUser( formRule.getNbDaysForMaxAppointmentsPerUser( ) );
        appointmentForm.setBoOverbooking( formRule.getBoOverbooking( ) );
    }

    /**
     * Fill the appointmentForm DTO with the form
     * 
     * @param appointmentForm
     *            the appointmentForm DTO
     * @param form
     *            the Form
     */
    private static void fillAppointmentFormWithFormPart( AppointmentFormDTO appointmentForm, Form form )
    {
        appointmentForm.setIdForm( form.getIdForm( ) );
        appointmentForm.setTitle( form.getTitle( ) );
        appointmentForm.setDescription( form.getDescription( ) );
        appointmentForm.setReference( form.getReference( ) );
        if ( form.getIdCategory( ) == null || form.getIdCategory( ) == 0 )
        {
            appointmentForm.setIdCategory( -1 );
        }
        else
        {
            appointmentForm.setIdCategory( form.getIdCategory( ) );
        }
        appointmentForm.setDateStartValidity( form.getStartingValiditySqlDate( ) );
        appointmentForm.setDateEndValidity( form.getEndingValiditySqlDate( ) );
        appointmentForm.setIdWorkflow( form.getIdWorkflow( ) );
        appointmentForm.setWorkgroup( form.getWorkgroup( ) );
        appointmentForm.setIsActive( form.getIsActive( ) );
        appointmentForm.setIsMultislotAppointment( form.getIsMultislotAppointment( ) );
    }

    /**
     * Fill the appointmentForm DTO with the display
     * 
     * @param appointmentForm
     *            the appointmentForm DTO
     * @param display
     *            the display
     */
    private static void fillAppointmentFormWithDisplayPart( AppointmentFormDTO appointmentForm, Display display )
    {
        appointmentForm.setDisplayTitleFo( display.isDisplayTitleFo( ) );
        appointmentForm.setIcon( display.getIcon( ) );
        appointmentForm.setNbWeeksToDisplay( display.getNbWeeksToDisplay( ) );
        appointmentForm.setIsDisplayedOnPortlet( display.isDisplayedOnPortlet( ) );
        appointmentForm.setCalendarTemplateId( display.getIdCalendarTemplate( ) );
    }

    /**
     * Fill the appointmentForm DTO with the localization
     * 
     * @param appointmentForm
     *            the appointmentForm DTO
     * @param localization
     *            the localization
     */
    private static void fillAppointmentFormWithLocalizationPart( AppointmentFormDTO appointmentForm, Localization localization )
    {
        if ( localization != null )
        {
            if ( localization.getLongitude( ) != null )
            {
                appointmentForm.setLongitude( localization.getLongitude( ) );
            }
            if ( localization.getLatitude( ) != null )
            {
                appointmentForm.setLatitude( localization.getLatitude( ) );
            }
            if ( localization.getAddress( ) != null )
            {
                appointmentForm.setAddress( localization.getAddress( ) );
            }
        }

    }

    /**
     * Check the validity of the form and update it if necessary
     * 
     * @param form
     *            the form to check
     */
    private static void checkValidityDate( Form form )
    {
        LocalDate dateNow = LocalDate.now( );
        if ( form.getStartingValidityDate( ) != null && !form.getIsActive( )
                && ( form.getStartingValidityDate( ).isBefore( dateNow ) || form.getStartingValidityDate( ).isEqual( dateNow ) )
                && ( form.getEndingValidityDate( ) == null || form.getEndingValidityDate( ).isAfter( dateNow )
                        || form.getEndingValidityDate( ).isEqual( dateNow ) ) )
        {
            form.setIsActive( true );
            FormService.updateForm( form );

        }
        else
            if ( form.getEndingValidityDate( ) != null && form.getIsActive( ) && form.getEndingValidityDate( ).isBefore( dateNow ) )
            {
                form.setIsActive( false );
                FormService.updateForm( form );
            }
    }

    /**
     * Create a form from an appointmentForm DTO
     * 
     * @param appointmentForm
     *            the appointmentForm DTO
     * @return the Form created
     */
    public static Form createForm( AppointmentFormDTO appointmentForm )
    {
        Form form = new Form( );
        form = fillInFormWithAppointmentForm( form, appointmentForm );
        FormHome.create( form );
        FormListenerManager.notifyListenersFormCreation( form.getIdForm( ) );
        return form;
    }

    /**
     * Update a form object with the values of the appointmentForm DTO
     * 
     * @param appointmentForm
     *            the appointmentForm DTO
     * @return the Form object updated
     */
    public static Form updateForm( AppointmentFormDTO appointmentForm )
    {
        Form form = FormService.findFormLightByPrimaryKey( appointmentForm.getIdForm( ) );
        form = fillInFormWithAppointmentForm( form, appointmentForm );
        FormService.updateForm( form );
        return form;
    }

    /**
     * Update a form
     * 
     * @param form
     *            the form
     * @return the form updated
     */
    public static Form updateForm( Form form )
    {
        Form formUpdated = FormHome.update( form );
        FormListenerManager.notifyListenersFormChange( formUpdated.getIdForm( ) );
        return formUpdated;
    }

    /**
     * Fill the form object with the values of the appointmentForm DTO
     * 
     * @param form
     *            the form object
     * @param appointmentForm
     *            the appointmentForm DTO
     * @return the form completed
     */
    public static Form fillInFormWithAppointmentForm( Form form, AppointmentFormDTO appointmentForm )
    {
        form.setTitle( appointmentForm.getTitle( ) );
        form.setDescription( appointmentForm.getDescription( ) );
        form.setReference( appointmentForm.getReference( ) );
        if ( appointmentForm.getIdCategory( ) == -1 )
        {
            form.setIdCategory( null );
        }
        else
        {
            form.setIdCategory( appointmentForm.getIdCategory( ) );
        }
        form.setStartingValiditySqlDate( appointmentForm.getDateStartValidity( ) );
        form.setEndingValiditySqlDate( appointmentForm.getDateEndValidity( ) );
        form.setIsActive( appointmentForm.getIsActive( ) );
        form.setIdWorkflow( appointmentForm.getIdWorkflow( ) );
        form.setWorkgroup( appointmentForm.getWorkgroup( ) );
        form.setIsMultislotAppointment( appointmentForm.getIsMultislotAppointment( ) );

        return form;
    }

    /**
     * Remove a Form from the database
     * 
     * @param nIdForm
     *            the form id to remove
     */
    public static void removeForm( int nIdForm )
    {
        // Delete all the responses linked to all the appointments of the form
        for ( Appointment appointment : AppointmentService.findListAppointmentByIdForm( nIdForm ) )
        {
            AppointmentResponseService.removeResponsesByIdAppointment( appointment.getIdAppointment( ) );
        }
        FormListenerManager.notifyListenersFormRemoval( nIdForm );
        AppointmentListenerManager.notifyListenersAppointmentFormRemoval( nIdForm );
        FormHome.delete( nIdForm );
    }

    /**
     * Find all the forms in the database
     * 
     * @return a list of all the forms
     */
    public static List<Form> findAllForms( )
    {
        return FormHome.findAllForms( );
    }

    /**
     * Find all the active forms in database
     * 
     * @return a list of all the active forms
     */
    public static List<Form> findAllActiveForms( )
    {
        return FormHome.findActiveForms( );
    }

    /**
     * Find all the active forms that have to be displayed on portlet in database
     * 
     * @return a list of all the found forms
     */
    public static List<Form> findAllActiveAndDisplayedOnPortletForms( )
    {
        return FormHome.findActiveAndDisplayedOnPortletForms( );
    }

    /**
     * find a form by its primary key
     * 
     * @param nIdForm
     *            the form Id
     * @return the Form
     */
    public static Form findFormLightByPrimaryKey( int nIdForm )
    {
        return FormHome.findByPrimaryKey( nIdForm );
    }

    /**
     * Find forms by the title
     * 
     * @param strTitle
     *            the form title
     * @return the Forms with this title
     */
    public static List<Form> findFormsByTitle( String strTitle )
    {
        return FormHome.findByTitle( strTitle );
    }

}
