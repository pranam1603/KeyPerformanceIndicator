package com.example.OneBusLoader;

import org.onebusaway.gtfs.model.*;
import org.onebusaway.gtfs.impl.GtfsDaoImpl;
import org.onebusaway.gtfs.serialization.GtfsReader;

import java.util.*;
import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalDate;

public class GTFSLoader {

    public GtfsDaoImpl dao;
    private LocalDate targetDate;

    public GTFSLoader(String path, LocalDate travelDate) throws Exception {
        this.targetDate = travelDate;
        dao = new GtfsDaoImpl();

        GtfsReader reader = new GtfsReader();
        reader.setInputLocation(new File(path));
        reader.setEntityStore(dao);
        reader.run();
    }

    public Collection<Stop> getAllStops() {
        return dao.getAllStops();
    }

    public Collection<StopTime> getAllStopTimes() {
        return dao.getAllStopTimes();
    }

    public List<Trip> getActiveTrips() {
        List<Trip> activeTrips = new ArrayList<>();

        Map<AgencyAndId, ServiceCalendar> calendarMap = new HashMap<>();
        for (ServiceCalendar cal : dao.getAllCalendars()) {
            calendarMap.put(cal.getServiceId(), cal);
        }

        Map<AgencyAndId, List<ServiceCalendarDate>> calendarDatesMap = new HashMap<>();
        for (ServiceCalendarDate cd : dao.getAllCalendarDates()) {
            calendarDatesMap
                    .computeIfAbsent(cd.getServiceId(), k -> new ArrayList<>())
                    .add(cd);
        }

        DayOfWeek dow = targetDate.getDayOfWeek();

        for (Trip trip : dao.getAllTrips()) {
            AgencyAndId serviceId = trip.getServiceId();
            boolean active = false;

            // 1️⃣ Check calendar_dates.txt exceptions first
            if (calendarDatesMap.containsKey(serviceId)) {
                for (ServiceCalendarDate cd : calendarDatesMap.get(serviceId)) {
                    LocalDate cdDate = LocalDate.of(
                            cd.getDate().getYear(),
                            cd.getDate().getMonth(),
                            cd.getDate().getDay()
                    );

                    if (cdDate.equals(targetDate)) {
                        active = cd.getExceptionType() == ServiceCalendarDate.EXCEPTION_TYPE_ADD;
                        break;
                    }
                }
            }

            // 2️⃣ Check calendar.txt if no specific exception
            if (!active && calendarMap.containsKey(serviceId)) {
                ServiceCalendar cal = calendarMap.get(serviceId);

                LocalDate start = LocalDate.of(cal.getStartDate().getYear(), cal.getStartDate().getMonth(), cal.getStartDate().getDay());
                LocalDate end = LocalDate.of(cal.getEndDate().getYear(), cal.getEndDate().getMonth(), cal.getEndDate().getDay());

                if (!targetDate.isBefore(start) && !targetDate.isAfter(end)) {
                    switch (dow) {
                        case MONDAY: active = cal.getMonday() == 1; break;
                        case TUESDAY: active = cal.getTuesday() == 1; break;
                        case WEDNESDAY: active = cal.getWednesday() == 1; break;
                        case THURSDAY: active = cal.getThursday() == 1; break;
                        case FRIDAY: active = cal.getFriday() == 1; break;
                        case SATURDAY: active = cal.getSaturday() == 1; break;
                        case SUNDAY: active = cal.getSunday() == 1; break;
                    }
                }
            }

            if (active) {
                activeTrips.add(trip);
            }
        }

        System.out.println("Total trips: " + dao.getAllTrips().size());
        System.out.println("Active trips on " + targetDate + ": " + activeTrips.size());

        return activeTrips;
    }

    public List<StopTime> getStopTimesForTrip(Trip trip) {
        List<StopTime> stopTimes = new ArrayList<>();

        for (StopTime st : dao.getAllStopTimes()) {
            if (st.getTrip().equals(trip)) {
                stopTimes.add(st);
            }
        }

        stopTimes.sort(Comparator.comparingInt(StopTime::getStopSequence));
        return stopTimes;
    }
}

