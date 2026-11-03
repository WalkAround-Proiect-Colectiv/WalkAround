import { useRef, useState } from 'react';
import {
  ActivityIndicator, Linking, Pressable, ScrollView, StyleSheet, Text, TextInput, View,
} from 'react-native';
import { Redirect, useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import {
  ArrowLeft, ArrowRight, BedDouble, CalendarDays, Check, ExternalLink, MapPin,
  Plane, Search, Sparkles, Ticket, Wallet,
} from 'lucide-react-native';
import { useSession } from '../../login/components/SessionProvider';
import CalendarPicker from '../components/CalendarPicker';
import AccommodationOfferCard from '../components/AccommodationOfferCard';
import {
  getCityAirports, searchAccommodations, searchCities, searchFlights,
  type AccommodationOffer, type Airport, type CitySuggestion, type FlightItinerary, type FlightSegment, type TicketOption,
} from '../api/tripPlanning';

const BLUE = '#0065EB';
const PURPLE = '#23012C';
const INK = '#202632';

function message(error: unknown): string {
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.';
}

function CityPicker({
  label, hint, selected, onSelect, onChange,
}: {
  label: string;
  hint: string;
  selected: CitySuggestion | null;
  onSelect(city: CitySuggestion): void;
  onChange(): void;
}) {
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState<CitySuggestion[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const searchId = useRef(0);

  async function searchCity(value = query) {
    const name = value.trim();
    if (name.length < 2) {
      setError('Enter at least two letters to search.');
      return;
    }

    const id = ++searchId.current;
    setBusy(true);
    setError(null);
    setSuggestions(null);
    try {
      const results = await searchCities(name);
      if (searchId.current === id) setSuggestions(results);
    } catch (reason) {
      if (searchId.current === id) setError(message(reason));
    } finally {
      if (searchId.current === id) setBusy(false);
    }
  }

  function choose(city: CitySuggestion) {
    searchId.current += 1;
    setQuery(city.name);
    setSuggestions(null);
    setError(null);
    onSelect(city);
  }

  return (
    <View style={styles.section}>
      <View style={styles.sectionHeading}>
        <View style={styles.iconBubble}><MapPin size={20} color={BLUE} /></View>
        <View style={styles.headingCopy}>
          <Text style={styles.sectionTitle}>{label}</Text>
          <Text style={styles.helper}>{hint}</Text>
        </View>
      </View>

      {selected ? (
        <View style={styles.selectedRow}>
          <View style={styles.selectedCopy}>
            <Text style={styles.selectedName}>{selected.name}</Text>
            <Text style={styles.helper}>{selected.country} · {selected.countryCode}</Text>
          </View>
          <Check size={20} color={BLUE} />
          <Pressable accessibilityRole="button" onPress={() => {
            onChange();
            setQuery('');
            setSuggestions(null);
          }} style={styles.textButton}>
            <Text style={styles.textButtonLabel}>Change</Text>
          </Pressable>
        </View>
      ) : (
        <>
          <View style={styles.searchRow}>
            <TextInput
              accessibilityLabel={`${label} city name`}
              autoCapitalize="words"
              autoCorrect={false}
              maxLength={100}
              onChangeText={(value) => {
                searchId.current += 1;
                setQuery(value);
                setSuggestions(null);
                setError(null);
                setBusy(false);
              }}
              onSubmitEditing={() => searchCity()}
              placeholder="Type a city name"
              placeholderTextColor="#8892A2"
              returnKeyType="search"
              style={styles.searchInput}
              value={query}
            />
            <Pressable accessibilityRole="button" accessibilityLabel={`Search ${label.toLowerCase()} cities`}
              disabled={busy} onPress={() => searchCity()} style={[styles.searchButton, busy && styles.disabled]}>
              {busy ? <ActivityIndicator color="#fff" size="small" /> : <Search size={19} color="#fff" />}
              <Text style={styles.searchButtonText}>Search</Text>
            </Pressable>
          </View>
          {error && <Text accessibilityRole="alert" style={styles.errorText}>{error}</Text>}
          {suggestions && (
            <View style={styles.suggestions}>
              <Text style={styles.suggestionsTitle}>RECOMMENDED MATCHES</Text>
              {suggestions.length === 0 ? <Text style={styles.emptyText}>No cities found. Try another spelling.</Text> :
                suggestions.map((city, index) => (
                  <Pressable key={`${city.name}-${city.countryCode}-${city.latitude}-${index}`}
                    accessibilityRole="button" accessibilityLabel={`Select ${city.name}, ${city.country}`}
                    onPress={() => choose(city)} style={styles.suggestionRow}>
                    <View style={styles.suggestionIcon}><MapPin size={17} color={BLUE} /></View>
                    <View style={styles.suggestionCopy}>
                      <Text style={styles.suggestionName}>{city.name}</Text>
                      <Text style={styles.helper}>{city.country}</Text>
                    </View>
                    <ArrowRight size={17} color="#7E8999" />
                  </Pressable>
                ))}
            </View>
          )}
        </>
      )}
    </View>
  );
}

function NumberField({
  label, value, onChange, suffix, placeholder,
}: { label: string; value: string; onChange(value: string): void; suffix?: string; placeholder: string }) {
  const [focused, setFocused] = useState(false);

  function handleChange(input: string) {
    if (!suffix) {
      onChange(input.replace(/\D/g, '').slice(0, 3));
      return;
    }

    const cleaned = input.replace(/[^\d.]/g, '');
    const [whole, ...fraction] = cleaned.split('.');
    onChange(fraction.length > 0
      ? `${whole || '0'}.${fraction.join('').slice(0, 2)}`
      : whole);
  }

  return <View style={styles.field}>
    <Text style={styles.fieldLabel}>{label}</Text>
    <View style={[styles.fieldInputWrap, focused && styles.fieldInputFocused]}>
      <TextInput accessibilityLabel={label} keyboardType={suffix ? 'decimal-pad' : 'number-pad'}
        onFocus={() => setFocused(true)} onBlur={() => setFocused(false)} onChangeText={handleChange}
        placeholder={placeholder} placeholderTextColor="#8892A2" style={styles.fieldInput} value={value} />
      {suffix && <Text style={styles.fieldSuffix}>{suffix}</Text>}
    </View>
  </View>;
}

function DateTimeFields({
  label, date, time, minimumDate, onDate, onTime,
}: { label: string; date: string; time: string; minimumDate?: string; onDate(value: string): void; onTime(value: string): void }) {
  const [calendarOpen, setCalendarOpen] = useState(false);
  const [timeFocused, setTimeFocused] = useState(false);

  function handleTime(input: string) {
    const cleaned = input.replace(/[^\d:]/g, '');
    if (cleaned.includes(':')) {
      const [hours, ...minutes] = cleaned.split(':');
      onTime(`${hours.slice(0, 2)}:${minutes.join('').slice(0, 2)}`);
      return;
    }

    const digits = cleaned.slice(0, 4);
    onTime(digits.length > 2 ? `${digits.slice(0, 2)}:${digits.slice(2)}` : digits);
  }

  return <View style={styles.field}>
    <Text style={styles.fieldLabel}>{label}</Text>
    <View style={styles.dateRow}>
      <Pressable accessibilityRole="button" accessibilityLabel={`Choose ${label.toLowerCase()} date`}
        onPress={() => setCalendarOpen(true)} style={[styles.fieldInputWrap, styles.dateInputWrap]}>
        <CalendarDays size={17} color="#7E8999" />
        <Text style={[styles.dateValue, !date && styles.datePlaceholder]}>{date || 'Choose date'}</Text>
      </Pressable>
      <View style={[styles.fieldInputWrap, styles.timeInputWrap, timeFocused && styles.fieldInputFocused]}>
        <TextInput accessibilityLabel={`${label} time`} keyboardType="number-pad"
          onFocus={() => setTimeFocused(true)} onBlur={() => setTimeFocused(false)}
          onChangeText={handleTime} placeholder="HH:MM" placeholderTextColor="#8892A2"
          style={styles.fieldInput} value={time} maxLength={5} />
      </View>
    </View>
    <CalendarPicker label={label} value={date} visible={calendarOpen} minimumDate={minimumDate}
      onSelect={onDate} onClose={() => setCalendarOpen(false)} />
  </View>;
}

function validDateTime(date: string, time: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !/^([01]\d|2[0-3]):[0-5]\d$/.test(time)) return false;
  const parsed = new Date(`${date}T${time}:00`);
  return !Number.isNaN(parsed.getTime()) &&
    `${parsed.getFullYear()}-${String(parsed.getMonth() + 1).padStart(2, '0')}-${String(parsed.getDate()).padStart(2, '0')}` === date;
}

function ticketDepartureDate(ticket: FlightItinerary | undefined): string | null {
  const departure = ticket?.outbound?.[0]?.departureTime;
  const date = departure?.slice(0, 10);
  return date && validDateTime(date, '12:00') ? date : null;
}

function timeLabel(value: string): string {
  return value ? value.replace('T', ' · ') : 'Time unavailable';
}

function SegmentList({ title, segments }: { title: string; segments: FlightSegment[] }) {
  return <View style={styles.segmentGroup}>
    <Text style={styles.segmentGroupTitle}>{title}</Text>
    {segments.map((segment, index) => <View key={`${segment.flightNumber}-${index}`} style={styles.segment}>
      <View style={styles.segmentRail}><View style={styles.railDot} /><View style={styles.railLine} /><View style={styles.railDot} /></View>
      <View style={styles.segmentBody}>
        <Text style={styles.segmentRoute}>{segment.departureAirport} → {segment.arrivalAirport}</Text>
        <Text style={styles.segmentTime}>{timeLabel(segment.departureTime)} → {timeLabel(segment.arrivalTime)}</Text>
        <Text style={styles.segmentAirline}>{segment.airline || 'Airline unavailable'}{segment.flightNumber ? ` · ${segment.flightNumber}` : ''}</Text>
      </View>
    </View>)}
  </View>;
}

function TicketCard({ ticket, index, selected, onSelect }: {
  ticket: FlightItinerary; index: number; selected: boolean; onSelect(): void;
}) {
  const [linkError, setLinkError] = useState<string | null>(null);

  async function openFlight(url: string) {
    try {
      const parsed = new URL(url);
      if (parsed.protocol !== 'https:' || parsed.hostname !== 'www.google.com' ||
          !parsed.pathname.startsWith('/travel/flights')) {
        throw new Error('This flight link is unavailable.');
      }
      await Linking.openURL(url);
      setLinkError(null);
    } catch {
      setLinkError('Could not open Google Flights. Please try again.');
    }
  }

  const price = new Intl.NumberFormat('en-US', { style: 'currency', currency: ticket.currency || 'EUR', maximumFractionDigits: 0 })
    .format(ticket.totalPrice);
  const outbound = ticket.outbound ?? [];
  const returning = ticket.returning ?? [];
  const urls = ticket.viewUrls ?? [];

  return <View style={[styles.ticketCard, selected && styles.ticketSelected]}>
    <Pressable accessibilityRole="button" accessibilityLabel={`${selected ? 'Hide' : 'Show'} details for ticket ${index + 1}, ${price}`}
      accessibilityState={{ expanded: selected }}
      onPress={onSelect} style={styles.ticketHeader}>
      <View style={styles.ticketNumber}><Ticket size={18} color={BLUE} /></View>
      <View style={styles.ticketMain}>
        <Text style={styles.ticketEyebrow}>OPTION {index + 1} · {ticket.ticketOption === 'ROUND_TRIP' ? 'ROUND TRIP' : 'TWO ONE-WAY'}</Text>
        <Text style={styles.ticketRoute}>{outbound[0]?.departureAirport || 'Origin'} → {outbound[outbound.length - 1]?.arrivalAirport || 'Destination'}</Text>
      </View>
      <View style={styles.ticketPriceWrap}>
        <Text style={styles.ticketPrice}>{price}</Text>
        <Text style={styles.ticketPriceHint}>total</Text>
      </View>
    </Pressable>
    <Pressable accessibilityRole="button" accessibilityLabel={`${selected ? 'Hide' : 'Show'} details for ticket ${index + 1}`}
      accessibilityState={{ expanded: selected }} onPress={onSelect} style={styles.ticketSummary}>
      <Text style={styles.ticketSummaryText}>{outbound.length} outbound flight{outbound.length === 1 ? '' : 's'} · {returning.length} return flight{returning.length === 1 ? '' : 's'}</Text>
      <Text style={styles.ticketDetailsToggle}>{selected ? 'Details shown' : 'Tap for details'} {selected ? '⌃' : '⌄'}</Text>
    </Pressable>
    {selected && <View style={styles.ticketDetails}>
      <SegmentList title="OUTBOUND" segments={outbound} />
      <SegmentList title="RETURN" segments={returning} />
      <Text style={styles.linkNote}>Google Flights opens the matching search. Prices and availability may change.</Text>
      {urls.map((url, urlIndex) => <Pressable key={`${url}-${urlIndex}`} accessibilityRole="link"
        onPress={() => openFlight(url)} style={styles.linkButton}>
        <ExternalLink size={16} color="#fff" />
        <Text style={styles.linkButtonText}>{urls.length > 1 ? `View ${urlIndex === 0 ? 'outbound' : 'return'} on Google Flights` : 'View on Google Flights'}</Text>
      </Pressable>)}
      {urls.length === 0 && <Text style={styles.helper}>No Google Flights link is available for this ticket.</Text>}
      {linkError && <Text accessibilityRole="alert" style={styles.errorText}>{linkError}</Text>}
    </View>}
  </View>;
}

export default function TripPlanningScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { session, ready } = useSession();
  const airportRequestId = useRef(0);
  const flightRequestId = useRef(0);
  const accommodationRequestId = useRef(0);
  const [departureCity, setDepartureCity] = useState<CitySuggestion | null>(null);
  const [destinationCity, setDestinationCity] = useState<CitySuggestion | null>(null);
  const [airports, setAirports] = useState<Airport[]>([]);
  const [airport, setAirport] = useState<Airport | null>(null);
  const [airportsBusy, setAirportsBusy] = useState(false);
  const [airportsError, setAirportsError] = useState<string | null>(null);
  const [budget, setBudget] = useState('');
  const [fromDate, setFromDate] = useState('');
  const [fromTime, setFromTime] = useState('');
  const [untilDate, setUntilDate] = useState('');
  const [untilTime, setUntilTime] = useState('');
  const [nights, setNights] = useState('');
  const [ticketOption, setTicketOption] = useState<TicketOption>('ROUND_TRIP');
  const [tickets, setTickets] = useState<FlightItinerary[] | null>(null);
  const [selectedTicket, setSelectedTicket] = useState<number | null>(null);
  const [searchBusy, setSearchBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [checkInDate, setCheckInDate] = useState('');
  const [checkInSource, setCheckInSource] = useState<'ticket' | 'calendar' | null>(null);
  const [checkInCalendarOpen, setCheckInCalendarOpen] = useState(false);
  const [accommodationBudget, setAccommodationBudget] = useState('');
  const [accommodations, setAccommodations] = useState<AccommodationOffer[] | null>(null);
  const [accommodationBusy, setAccommodationBusy] = useState(false);
  const [accommodationError, setAccommodationError] = useState<string | null>(null);

  function clearAccommodations() {
    accommodationRequestId.current += 1;
    setAccommodations(null);
    setAccommodationError(null);
  }

  function clearTickets() {
    flightRequestId.current += 1;
    setTickets(null);
    setSelectedTicket(null);
    setFormError(null);
    if (checkInSource === 'ticket') {
      setCheckInDate('');
      setCheckInSource(null);
      clearAccommodations();
    }
  }

  function chooseTicket(index: number) {
    if (selectedTicket === index) {
      setSelectedTicket(null);
      if (checkInSource === 'ticket') setCheckInSource('calendar');
      return;
    }

    setSelectedTicket(index);
    const date = ticketDepartureDate(tickets?.[index]);
    if (date) {
      setCheckInDate(date);
      setCheckInSource('ticket');
      clearAccommodations();
    } else if (checkInSource === 'ticket') {
      setCheckInDate('');
      setCheckInSource(null);
      clearAccommodations();
    }
  }

  async function submitAccommodations() {
    if (accommodationBusy) return;
    setAccommodationError(null);
    if (!destinationCity) {
      setAccommodationError('Choose a destination city first.');
      return;
    }
    if (!validDateTime(checkInDate, '12:00')) {
      setAccommodationError('Choose a check-in date from the calendar or a flight.');
      return;
    }
    const stay = Number(nights);
    if (!/^\d+$/.test(nights) || stay < 1 || stay > 365) {
      setAccommodationError('Enter a stay of 1 to 365 nights.');
      return;
    }
    const amount = Number(accommodationBudget);
    if (!/^\d+(\.\d{1,2})?$/.test(accommodationBudget) || amount <= 0 || amount > 999_999_999.99) {
      setAccommodationError('Enter an accommodation budget greater than zero, in EUR.');
      return;
    }

    const id = ++accommodationRequestId.current;
    setAccommodations(null);
    setAccommodationBusy(true);
    try {
      const results = await searchAccommodations({
        destinationCity: destinationCity.lookupName || destinationCity.name,
        countryCode: destinationCity.countryCode,
        checkInDate,
        stayingNights: stay,
        budget: amount,
      });
      if (accommodationRequestId.current === id) setAccommodations(results);
    } catch (reason) {
      if (accommodationRequestId.current === id) setAccommodationError(message(reason));
    } finally {
      setAccommodationBusy(false);
    }
  }

  async function selectDeparture(city: CitySuggestion) {
    clearTickets();
    const id = ++airportRequestId.current;
    setDepartureCity(city);
    setAirport(null);
    setAirports([]);
    setAirportsError(null);
    setAirportsBusy(true);
    try {
      const results = await getCityAirports(city);
      if (airportRequestId.current === id) setAirports(results);
    } catch (reason) {
      if (airportRequestId.current === id) setAirportsError(message(reason));
    } finally {
      if (airportRequestId.current === id) setAirportsBusy(false);
    }
  }

  function changeDeparture() {
    airportRequestId.current += 1;
    setDepartureCity(null);
    setAirport(null);
    setAirports([]);
    setAirportsBusy(false);
    setAirportsError(null);
    clearTickets();
  }

  async function submit() {
    if (searchBusy) return;
    setFormError(null);
    if (!airport?.iataCode || !destinationCity) {
      setFormError('Choose a departure airport and destination city first.');
      return;
    }
    const amount = Number(budget);
    const stay = Number(nights);
    if (!/^\d+(\.\d{1,2})?$/.test(budget) || amount <= 0 || amount > 999_999_999.99) {
      setFormError('Enter a flight budget greater than zero, in EUR.');
      return;
    }
    if (!/^\d+$/.test(nights) || stay < 1 || stay > 365) {
      setFormError('Enter a stay of 1 to 365 nights.');
      return;
    }
    if (!validDateTime(fromDate, fromTime) || !validDateTime(untilDate, untilTime)) {
      setFormError('Enter both dates as YYYY-MM-DD and times as HH:MM.');
      return;
    }
    const from = `${fromDate}T${fromTime}:00`;
    const until = `${untilDate}T${untilTime}:00`;
    if (until < from) {
      setFormError('The last departure time must be after the first.');
      return;
    }

    clearTickets();
    const id = ++flightRequestId.current;
    setSearchBusy(true);
    try {
      const results = await searchFlights({
        originAirport: airport.iataCode,
        destinationCity: destinationCity.lookupName || destinationCity.name,
        countryCode: destinationCity.countryCode,
        budget: amount,
        departureFrom: from,
        departureUntil: until,
        stayingNights: stay,
        ticketOption,
      });
      if (flightRequestId.current === id) setTickets(results);
    } catch (reason) {
      if (flightRequestId.current === id) setFormError(message(reason));
    } finally {
      setSearchBusy(false);
    }
  }

  if (!ready) return <View style={styles.center}><ActivityIndicator color={BLUE} /></View>;
  if (!session) return <Redirect href="/login" />;

  return <View style={styles.screen}>
    <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={[styles.scroll, { paddingTop: Math.max(insets.top, 16), paddingBottom: Math.max(insets.bottom, 24) + 28 }]}>
      <View style={styles.content}>
        <View style={styles.topBar}>
          <Pressable accessibilityRole="button" accessibilityLabel="Back to map"
            onPress={() => router.push('/map')} style={styles.backButton}>
            <ArrowLeft size={21} color={INK} />
          </Pressable>
          <Text style={styles.brand}>WalkAround</Text>
          <View style={styles.topSpacer} />
        </View>

        <View style={styles.hero}>
          <View style={styles.heroDecorOne} /><View style={styles.heroDecorTwo} />
          <View style={styles.heroBadge}><Sparkles size={15} color="#fff" /><Text style={styles.heroBadgeText}>YOUR NEXT ADVENTURE</Text></View>
          <Text accessibilityRole="header" style={styles.heroTitle}>Where to next?</Text>
          <Text style={styles.heroSubtitle}>A few choices, and your trip starts taking shape.</Text>
          <View style={styles.heroRoute}><View style={styles.routeDot} /><View style={styles.routeLine} /><Plane size={22} color="#fff" /></View>
        </View>

        <View style={styles.card}>
          <View style={styles.stepRow}><Text style={styles.stepNumber}>01</Text><Text style={styles.stepLabel}>START YOUR ROUTE</Text></View>
          <CityPicker label="Leaving from" hint="Choose your departure city"
            selected={departureCity} onSelect={selectDeparture} onChange={changeDeparture} />

          {departureCity && <View style={styles.airportSection}>
            <Text style={styles.fieldLabel}>Choose your airport</Text>
            {airportsBusy ? <View style={styles.loadingRow}><ActivityIndicator color={BLUE} /><Text style={styles.helper}>Finding nearby airports…</Text></View> : null}
            {airportsError && <Text accessibilityRole="alert" style={styles.errorText}>{airportsError}</Text>}
            {!airportsBusy && !airportsError && airports.length === 0 && <Text style={styles.emptyText}>No airports are listed for this city. Try another departure city.</Text>}
            {airports.map((candidate) => <Pressable key={candidate.id} accessibilityRole="radio"
              accessibilityState={{ selected: airport?.id === candidate.id, disabled: !candidate.iataCode }}
              disabled={!candidate.iataCode} onPress={() => { setAirport(candidate); clearTickets(); }}
              style={[styles.airportOption, airport?.id === candidate.id && styles.airportSelected, !candidate.iataCode && styles.disabled]}>
              <View style={[styles.airportIcon, airport?.id === candidate.id && styles.airportIconSelected]}><Plane size={18} color={airport?.id === candidate.id ? '#fff' : BLUE} /></View>
              <View style={styles.airportCopy}><Text style={styles.airportName}>{candidate.name}</Text><Text style={styles.helper}>{candidate.iataCode || 'No IATA code · unavailable for flight search'}</Text></View>
              <View style={[styles.radio, airport?.id === candidate.id && styles.radioSelected]}>{airport?.id === candidate.id && <Check size={13} color="#fff" />}</View>
            </Pressable>)}
          </View>}

          <View style={styles.routeDivider}><View style={styles.dividerLine} /><Plane size={17} color={PURPLE} /><View style={styles.dividerLine} /></View>
          <CityPicker label="Going to" hint="Choose your destination city"
            selected={destinationCity} onSelect={(city) => { setDestinationCity(city); clearTickets(); clearAccommodations(); }}
            onChange={() => { setDestinationCity(null); clearTickets(); clearAccommodations(); }} />
        </View>

        <View style={styles.card}>
          <View style={styles.stepRow}><Text style={styles.stepNumber}>02</Text><Text style={styles.stepLabel}>MAKE IT YOURS</Text></View>
          <View style={styles.sectionHeading}><View style={styles.iconBubble}><Wallet size={20} color={BLUE} /></View><View style={styles.headingCopy}><Text style={styles.sectionTitle}>Your travel details</Text><Text style={styles.helper}>Set your budget and dates</Text></View></View>
          <NumberField label="Flight budget" value={budget} suffix="EUR" placeholder="e.g. 300"
            onChange={(value) => { setBudget(value); clearTickets(); }} />
          <Text style={styles.fieldLabel}>Departure period</Text>
          <Text style={styles.helper}>We’ll look for flights leaving within this window. A shorter window searches faster.</Text>
          <DateTimeFields label="From" date={fromDate} time={fromTime}
            onDate={(value) => { setFromDate(value); clearTickets(); }} onTime={(value) => { setFromTime(value); clearTickets(); }} />
          <DateTimeFields label="Until" date={untilDate} time={untilTime} minimumDate={fromDate}
            onDate={(value) => { setUntilDate(value); clearTickets(); }} onTime={(value) => { setUntilTime(value); clearTickets(); }} />
          <NumberField label="Nights away" value={nights} placeholder="e.g. 3"
            onChange={(value) => { setNights(value); clearTickets(); clearAccommodations(); }} />
          <Text style={styles.fieldLabel}>Ticket style</Text>
          <View style={styles.optionRow}>
            <Pressable accessibilityRole="radio" accessibilityState={{ selected: ticketOption === 'ROUND_TRIP' }}
              onPress={() => { setTicketOption('ROUND_TRIP'); clearTickets(); }} style={[styles.option, ticketOption === 'ROUND_TRIP' && styles.optionSelected]}>
              <Text style={[styles.optionTitle, ticketOption === 'ROUND_TRIP' && styles.optionTitleSelected]}>Round trip</Text>
              <Text style={styles.optionHelp}>One return booking</Text>
            </Pressable>
            <Pressable accessibilityRole="radio" accessibilityState={{ selected: ticketOption === 'TWO_ONE_WAY' }}
              onPress={() => { setTicketOption('TWO_ONE_WAY'); clearTickets(); }} style={[styles.option, ticketOption === 'TWO_ONE_WAY' && styles.optionSelected]}>
              <Text style={[styles.optionTitle, ticketOption === 'TWO_ONE_WAY' && styles.optionTitleSelected]}>Two one-way</Text>
              <Text style={styles.optionHelp}>Separate tickets</Text>
            </Pressable>
          </View>
          {formError && <Text accessibilityRole="alert" style={styles.errorText}>{formError}</Text>}
          <Pressable accessibilityRole="button" disabled={searchBusy} onPress={submit}
            style={[styles.primaryButton, searchBusy && styles.disabled]}>
            {searchBusy ? <ActivityIndicator color="#fff" /> : <Search size={20} color="#fff" />}
            <Text style={styles.primaryButtonText}>{searchBusy ? 'Finding your flights…' : 'Find flights'}</Text>
            {!searchBusy && <ArrowRight size={19} color="#fff" />}
          </Pressable>
        </View>

        {(searchBusy || tickets) && <View style={styles.results}>
          <Text style={styles.stepLabel}>FLIGHT OPTIONS</Text>
          <Text style={styles.resultsTitle}>Flights for your trip</Text>
          <Text style={styles.helper}>{airport?.iataCode} to {destinationCity?.name} · {nights} night{nights === '1' ? '' : 's'}</Text>
          {searchBusy && <View style={styles.searchingPanel}><ActivityIndicator color={BLUE} /><Text style={styles.searchingText}>Searching available flights…</Text><Text style={styles.helper}>This can take a little while.</Text></View>}
          {tickets?.length === 0 && <View style={styles.searchingPanel}><Text style={styles.emptyTitle}>No flights found just yet</Text><Text style={styles.helper}>Try a wider departure period or a larger budget.</Text></View>}
          {tickets?.map((ticket, index) => <TicketCard key={`${ticket.totalPrice}-${index}`} ticket={ticket} index={index}
            selected={selectedTicket === index} onSelect={() => chooseTicket(index)} />)}
        </View>}

        <View style={styles.card}>
          <View style={styles.stepRow}><Text style={styles.stepNumber}>03</Text><Text style={styles.stepLabel}>FIND YOUR STAY</Text></View>
          <View style={styles.sectionHeading}>
            <View style={styles.iconBubble}><BedDouble size={20} color={BLUE} /></View>
            <View style={styles.headingCopy}>
              <Text style={styles.sectionTitle}>A place to stay</Text>
              <Text style={styles.helper}>{destinationCity ? `In ${destinationCity.name}, ${destinationCity.country}` : 'Choose a destination city above'}</Text>
            </View>
          </View>
          <Text style={styles.helper}>Select a flight to use its departure date, or choose your first night directly.</Text>
          <View style={styles.field}>
            <Text style={styles.fieldLabel}>Check-in date · first night</Text>
            <Pressable accessibilityRole="button" accessibilityLabel="Choose accommodation check-in date"
              onPress={() => setCheckInCalendarOpen(true)} style={[styles.fieldInputWrap, styles.checkInInput]}>
              <CalendarDays size={18} color={BLUE} />
              <Text style={[styles.dateValue, !checkInDate && styles.datePlaceholder]}>{checkInDate || 'Choose a date'}</Text>
              <Text style={styles.dateAction}>Change</Text>
            </Pressable>
            {checkInSource === 'ticket' && <Text style={styles.sourceHint}>Suggested from the selected flight’s departure date. You can change it.</Text>}
            {checkInSource === 'calendar' && checkInDate && <Text style={styles.sourceHint}>Date chosen from the calendar.</Text>}
            <CalendarPicker label="Check-in" description="Select the first night of your stay" value={checkInDate}
              visible={checkInCalendarOpen} onSelect={(date) => {
                setCheckInDate(date);
                setCheckInSource('calendar');
                clearAccommodations();
              }} onClose={() => setCheckInCalendarOpen(false)} />
          </View>
          <NumberField label="Accommodation budget" value={accommodationBudget} suffix="EUR" placeholder="e.g. 450"
            onChange={(value) => { setAccommodationBudget(value); clearAccommodations(); }} />
          <Text style={styles.helper}>Total budget for {nights || 'your'} night{nights === '1' ? '' : 's'}, including taxes and fees, for one adult.</Text>
          {accommodationError && <Text accessibilityRole="alert" style={styles.errorText}>{accommodationError}</Text>}
          <Pressable accessibilityRole="button" disabled={accommodationBusy} onPress={submitAccommodations}
            style={[styles.primaryButton, accommodationBusy && styles.disabled]}>
            {accommodationBusy ? <ActivityIndicator color="#fff" /> : <Search size={20} color="#fff" />}
            <Text style={styles.primaryButtonText}>{accommodationBusy ? 'Finding stays…' : 'Find stays'}</Text>
            {!accommodationBusy && <ArrowRight size={19} color="#fff" />}
          </Pressable>
        </View>

        {(accommodationBusy || accommodations) && <View style={styles.results}>
          <Text style={styles.stepLabel}>STAY OPTIONS</Text>
          <Text style={styles.resultsTitle}>Stays in {destinationCity?.name}</Text>
          <Text style={styles.helper}>{checkInDate} · {nights} night{nights === '1' ? '' : 's'} · one adult</Text>
          {accommodationBusy && <View style={styles.searchingPanel}>
            <ActivityIndicator color={BLUE} />
            <Text style={styles.searchingText}>Finding places to stay…</Text>
            <Text style={styles.helper}>Checking full-stay prices and available details.</Text>
          </View>}
          {accommodations?.length === 0 && <View style={styles.searchingPanel}>
            <Text style={styles.emptyTitle}>No stays found in this budget</Text>
            <Text style={styles.helper}>Try another check-in date or a larger accommodation budget.</Text>
          </View>}
          {accommodations?.map((offer, index) => <AccommodationOfferCard
            key={`${offer.name}-${offer.checkInDate}-${index}`} offer={offer} index={index} />)}
        </View>}
      </View>
    </ScrollView>
  </View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#F4F7FB' },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  scroll: { paddingHorizontal: 16 },
  content: { width: '100%', maxWidth: 680, alignSelf: 'center', gap: 18 },
  topBar: { height: 54, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  backButton: { width: 42, height: 42, backgroundColor: '#fff', borderRadius: 16, alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  brand: { color: BLUE, fontSize: 18, fontWeight: '800', letterSpacing: -0.5 },
  topSpacer: { width: 42 },
  hero: { backgroundColor: BLUE, borderRadius: 28, padding: 24, minHeight: 216, overflow: 'hidden' },
  heroDecorOne: { position: 'absolute', width: 190, height: 190, borderRadius: 95, backgroundColor: '#3D8BFA', right: -55, top: -70, opacity: 0.58 },
  heroDecorTwo: { position: 'absolute', width: 115, height: 115, borderRadius: 60, borderWidth: 22, borderColor: '#8CC0FF', right: 55, bottom: -75, opacity: 0.26 },
  heroBadge: { alignSelf: 'flex-start', flexDirection: 'row', alignItems: 'center', gap: 7, backgroundColor: '#FFFFFF25', paddingHorizontal: 11, paddingVertical: 7, borderRadius: 18 },
  heroBadgeText: { color: '#fff', fontWeight: '800', fontSize: 10, letterSpacing: 1.2 },
  heroTitle: { color: '#fff', fontSize: 34, fontWeight: '800', letterSpacing: -1.2, marginTop: 22 },
  heroSubtitle: { color: '#E8F2FF', fontSize: 15, lineHeight: 21, marginTop: 6, maxWidth: 270 },
  heroRoute: { position: 'absolute', right: 25, bottom: 30, width: 105, flexDirection: 'row', alignItems: 'center' },
  routeDot: { width: 12, height: 12, borderWidth: 3, borderColor: '#fff', borderRadius: 6 },
  routeLine: { flex: 1, borderTopWidth: 2, borderStyle: 'dashed', borderColor: '#fff', marginHorizontal: 8 },
  card: { backgroundColor: '#fff', borderRadius: 26, padding: 20, gap: 17, borderWidth: 1, borderColor: '#E9EEF4' },
  stepRow: { flexDirection: 'row', alignItems: 'center', gap: 9 },
  stepNumber: { color: BLUE, fontWeight: '800', fontSize: 12, letterSpacing: 1 },
  stepLabel: { color: '#7A8595', fontWeight: '800', fontSize: 11, letterSpacing: 1.2 },
  section: { gap: 13 },
  sectionHeading: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  iconBubble: { width: 43, height: 43, borderRadius: 15, backgroundColor: '#EAF3FF', justifyContent: 'center', alignItems: 'center' },
  headingCopy: { flex: 1 },
  sectionTitle: { color: INK, fontSize: 18, fontWeight: '800' },
  helper: { color: '#7A8595', fontSize: 13, lineHeight: 19 },
  searchRow: { flexDirection: 'row', gap: 8 },
  searchInput: { flex: 1, minWidth: 0, height: 50, borderRadius: 14, borderWidth: 1, borderColor: '#DBE3ED', paddingHorizontal: 14, color: INK, fontSize: 15, backgroundColor: '#FBFCFE' },
  searchButton: { minWidth: 92, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, borderRadius: 14, backgroundColor: BLUE, paddingHorizontal: 11 },
  searchButtonText: { color: '#fff', fontWeight: '800', fontSize: 13 },
  disabled: { opacity: 0.48 },
  errorText: { color: '#C51B2D', fontSize: 13, lineHeight: 19 },
  suggestions: { backgroundColor: '#F8FAFD', borderWidth: 1, borderColor: '#E4EAF2', borderRadius: 16, paddingHorizontal: 13, paddingTop: 13 },
  suggestionsTitle: { color: '#7A8595', fontWeight: '800', fontSize: 10, letterSpacing: 1, marginBottom: 5 },
  suggestionRow: { minHeight: 62, borderTopWidth: 1, borderColor: '#E9EDF3', flexDirection: 'row', alignItems: 'center', gap: 10 },
  suggestionIcon: { width: 34, height: 34, borderRadius: 12, backgroundColor: '#EAF3FF', alignItems: 'center', justifyContent: 'center' },
  suggestionCopy: { flex: 1 },
  suggestionName: { color: INK, fontWeight: '700', fontSize: 15 },
  emptyText: { color: '#697587', fontSize: 14, lineHeight: 21, paddingVertical: 12 },
  selectedRow: { borderRadius: 16, borderWidth: 1, borderColor: '#A9CAFC', backgroundColor: '#F1F7FF', padding: 13, flexDirection: 'row', alignItems: 'center', gap: 8 },
  selectedCopy: { flex: 1 },
  selectedName: { color: INK, fontWeight: '800', fontSize: 16 },
  textButton: { padding: 7 },
  textButtonLabel: { color: BLUE, fontWeight: '800', fontSize: 13 },
  airportSection: { gap: 9 },
  fieldLabel: { color: INK, fontWeight: '700', fontSize: 14 },
  loadingRow: { flexDirection: 'row', gap: 10, alignItems: 'center', padding: 12 },
  airportOption: { minHeight: 70, borderWidth: 1, borderColor: '#E1E7EF', borderRadius: 16, flexDirection: 'row', alignItems: 'center', gap: 10, padding: 11 },
  airportSelected: { borderColor: BLUE, backgroundColor: '#F4F8FF' },
  airportIcon: { width: 39, height: 39, borderRadius: 14, backgroundColor: '#ECF4FF', alignItems: 'center', justifyContent: 'center' },
  airportIconSelected: { backgroundColor: BLUE },
  airportCopy: { flex: 1 },
  airportName: { color: INK, fontWeight: '700', fontSize: 14 },
  radio: { width: 20, height: 20, borderRadius: 10, borderWidth: 1.5, borderColor: '#A8B2C1', alignItems: 'center', justifyContent: 'center' },
  radioSelected: { backgroundColor: BLUE, borderColor: BLUE },
  routeDivider: { flexDirection: 'row', alignItems: 'center', gap: 12, marginVertical: 1 },
  dividerLine: { flex: 1, height: 1, backgroundColor: '#E8ECF2' },
  field: { gap: 8 },
  fieldInputWrap: { height: 50, flexDirection: 'row', alignItems: 'center', gap: 8, paddingHorizontal: 14, borderWidth: 1, borderColor: '#DBE3ED', borderRadius: 14, backgroundColor: '#FBFCFE' },
  fieldInputFocused: { borderColor: BLUE, backgroundColor: '#fff' },
  fieldInput: { flex: 1, minWidth: 0, color: INK, fontSize: 15, paddingVertical: 0, outlineWidth: 0 },
  fieldSuffix: { color: '#697587', fontWeight: '800', fontSize: 13 },
  dateRow: { flexDirection: 'row', gap: 8 },
  dateInputWrap: { flex: 1.55, minWidth: 0 },
  checkInInput: { justifyContent: 'flex-start' },
  dateValue: { color: INK, fontSize: 14, fontWeight: '600' },
  datePlaceholder: { color: '#8892A2', fontWeight: '400' },
  dateAction: { marginLeft: 'auto', color: BLUE, fontSize: 12, fontWeight: '800' },
  sourceHint: { color: '#66758A', fontSize: 12, lineHeight: 18 },
  timeInputWrap: { flex: 0.85, minWidth: 0 },
  optionRow: { flexDirection: 'row', gap: 8 },
  option: { flex: 1, borderWidth: 1, borderColor: '#DBE3ED', borderRadius: 15, padding: 13, gap: 4 },
  optionSelected: { borderColor: BLUE, backgroundColor: '#F1F7FF' },
  optionTitle: { color: INK, fontWeight: '800', fontSize: 14 },
  optionTitleSelected: { color: BLUE },
  optionHelp: { color: '#7A8595', fontSize: 12 },
  primaryButton: { height: 54, marginTop: 3, borderRadius: 16, backgroundColor: BLUE, flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: 10 },
  primaryButtonText: { color: '#fff', fontWeight: '800', fontSize: 16 },
  results: { gap: 12, paddingTop: 10 },
  resultsTitle: { color: INK, fontSize: 25, fontWeight: '800', letterSpacing: -0.5 },
  searchingPanel: { backgroundColor: '#fff', borderRadius: 22, padding: 26, alignItems: 'center', gap: 10, borderWidth: 1, borderColor: '#E9EEF4' },
  searchingText: { color: INK, fontSize: 15, fontWeight: '700' },
  emptyTitle: { color: INK, fontSize: 18, fontWeight: '800' },
  ticketCard: { backgroundColor: '#fff', borderWidth: 1, borderColor: '#E3EAF2', borderRadius: 20, overflow: 'hidden' },
  ticketSelected: { borderColor: BLUE, borderWidth: 1.5 },
  ticketHeader: { flexDirection: 'row', alignItems: 'center', gap: 10, padding: 15 },
  ticketNumber: { width: 38, height: 38, borderRadius: 13, backgroundColor: '#EAF3FF', alignItems: 'center', justifyContent: 'center' },
  ticketMain: { flex: 1 },
  ticketEyebrow: { color: '#7A8595', fontWeight: '800', fontSize: 10, letterSpacing: 0.6 },
  ticketRoute: { color: INK, fontWeight: '800', fontSize: 17, marginTop: 3 },
  ticketPriceWrap: { alignItems: 'flex-end' },
  ticketPrice: { color: BLUE, fontWeight: '800', fontSize: 21 },
  ticketPriceHint: { color: '#7A8595', fontSize: 11 },
  ticketSummary: { borderTopWidth: 1, borderColor: '#EDF1F5', flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 15, paddingVertical: 11, gap: 4 },
  ticketSummaryText: { color: '#697587', fontSize: 12 },
  ticketDetailsToggle: { color: BLUE, fontSize: 12, fontWeight: '700' },
  ticketDetails: { borderTopWidth: 1, borderColor: '#EDF1F5', padding: 15, gap: 15 },
  segmentGroup: { gap: 9 },
  segmentGroupTitle: { color: PURPLE, fontWeight: '800', fontSize: 11, letterSpacing: 1 },
  segment: { flexDirection: 'row', gap: 11 },
  segmentRail: { width: 12, alignItems: 'center', paddingTop: 4 },
  railDot: { width: 8, height: 8, borderRadius: 4, backgroundColor: PURPLE },
  railLine: { height: 31, width: 2, backgroundColor: '#D3C4D8' },
  segmentBody: { flex: 1, paddingBottom: 8 },
  segmentRoute: { color: INK, fontSize: 15, fontWeight: '800' },
  segmentTime: { color: '#566276', fontSize: 12, marginTop: 3 },
  segmentAirline: { color: '#7A8595', fontSize: 12, marginTop: 3 },
  linkNote: { color: '#697587', fontSize: 12, lineHeight: 18 },
  linkButton: { minHeight: 44, borderRadius: 13, backgroundColor: BLUE, alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 8, paddingHorizontal: 12 },
  linkButtonText: { color: '#fff', fontWeight: '800', fontSize: 13 },
});
