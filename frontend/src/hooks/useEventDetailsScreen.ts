import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Linking } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useSession } from '../login/components/SessionProvider';
import {
  EventsApiError,
  getEventDetails,
  markInterest,
  unmarkInterest,
  type EnrichmentStatus,
  type EventDetailsItem,
  type EventDetailsResponse,
  type InterestState,
} from '../events/api/events';
import { buildMapsUrl, splitTicketLinks, type TicketLinkGroups } from '../events/details';
import { isSafeLink } from '../events/format';

export type EventDetailsScreenStatus =
  | 'incomplete-link'
  | 'loading'
  | 'ready'
  | 'not-found'
  | 'error';

interface FetchResultState {
  targetKey: string;
  details: EventDetailsResponse | null;
  error: string | null;
  notFound: boolean;
}

function firstParam(value: string | string[] | undefined): string {
  return ((Array.isArray(value) ? value[0] : value) ?? '').trim();
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.';
}

export function useEventDetailsScreen() {
  const router = useRouter();
  const { session, ready } = useSession();
  const params = useLocalSearchParams<{ localityKey?: string; eventKey?: string }>();
  const localityKey = firstParam(params.localityKey);
  const eventKey = firstParam(params.eventKey);
  const requestTargetKey = `${localityKey}|${eventKey}`;

  const [fetchState, setFetchState] = useState<FetchResultState | null>(null);
  const [saving, setSaving] = useState(false);
  const [actionErrorState, setActionErrorState] = useState<{
    targetKey: string;
    message: string;
  } | null>(null);
  const [expandedKey, setExpandedKey] = useState<string | null>(null);
  const [reloadNonce, setReloadNonce] = useState(0);

  const mountedRef = useRef(true);
  const requestIdRef = useRef(0);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const signedIn = Boolean(session);

  useEffect(() => {
    if (!signedIn || !localityKey || !eventKey) {
      return;
    }

    const currentRequestId = ++requestIdRef.current;
    const currentTargetKey = `${localityKey}|${eventKey}|${reloadNonce}`;

    getEventDetails(localityKey, eventKey)
      .then(result => {
        if (!mountedRef.current || currentRequestId !== requestIdRef.current) {
          return;
        }
        setFetchState({
          targetKey: currentTargetKey,
          details: result,
          error: null,
          notFound: false,
        });
      })
      .catch(failure => {
        if (!mountedRef.current || currentRequestId !== requestIdRef.current) {
          return;
        }
        if (failure instanceof EventsApiError && failure.status === 404) {
          setFetchState({
            targetKey: currentTargetKey,
            details: null,
            error: null,
            notFound: true,
          });
          return;
        }
        setFetchState({
          targetKey: currentTargetKey,
          details: null,
          error: errorMessage(failure),
          notFound: false,
        });
      });
  }, [signedIn, localityKey, eventKey, reloadNonce]);

  const activeTargetKey = `${localityKey}|${eventKey}|${reloadNonce}`;
  const currentResult =
    fetchState && fetchState.targetKey === activeTargetKey ? fetchState : null;

  const status: EventDetailsScreenStatus = !localityKey || !eventKey
    ? 'incomplete-link'
    : !currentResult
      ? 'loading'
      : currentResult.notFound
        ? 'not-found'
        : currentResult.error
          ? 'error'
          : 'ready';

  const details = currentResult?.details ?? null;
  const error = currentResult?.error ?? null;
  const actionError =
    actionErrorState && actionErrorState.targetKey === requestTargetKey
      ? actionErrorState.message
      : null;
  const descriptionExpanded = expandedKey === requestTargetKey;

  const reload = useCallback(() => {
    setActionErrorState(null);
    setReloadNonce(current => current + 1);
  }, []);

  const navigateBack = useCallback(() => {
    if (router.canGoBack()) {
      router.back();
    } else {
      router.replace('/events');
    }
  }, [router]);

  const performInterestUpdate = useCallback(
    async (action: () => Promise<unknown>) => {
      if (!localityKey || !eventKey) {
        return;
      }
      setSaving(true);
      setActionErrorState(null);
      try {
        await action();
        const refreshed = await getEventDetails(localityKey, eventKey);
        if (mountedRef.current) {
          setFetchState({
            targetKey: activeTargetKey,
            details: refreshed,
            error: null,
            notFound: false,
          });
        }
      } catch (failure) {
        if (mountedRef.current) {
          setActionErrorState({
            targetKey: requestTargetKey,
            message: errorMessage(failure),
          });
        }
      } finally {
        if (mountedRef.current) {
          setSaving(false);
        }
      }
    },
    [localityKey, eventKey, activeTargetKey, requestTargetKey],
  );

  const toggleInterest = useCallback(() => {
    if (!details || details.event.past) {
      return;
    }
    const targetLocality = details.event.localityKey || localityKey;
    void performInterestUpdate(() =>
      details.interest
        ? unmarkInterest(eventKey)
        : markInterest(targetLocality, eventKey, null),
    );
  }, [details, localityKey, eventKey, performInterestUpdate]);

  const setReminder = useCallback(
    (minutes: number | null) => {
      if (!details || details.event.past) {
        return;
      }
      const targetLocality = details.event.localityKey || localityKey;
      void performInterestUpdate(() => markInterest(targetLocality, eventKey, minutes));
    },
    [details, localityKey, eventKey, performInterestUpdate],
  );

  const openLink = useCallback(
    async (url: string | null | undefined) => {
      if (!isSafeLink(url)) {
        return;
      }
      setActionErrorState(null);
      try {
        await Linking.openURL(url);
      } catch {
        if (mountedRef.current) {
          setActionErrorState({
            targetKey: requestTargetKey,
            message: 'Could not open the external link on this device.',
          });
        }
      }
    },
    [requestTargetKey],
  );

  const toggleDescriptionExpanded = useCallback(() => {
    setExpandedKey(current => (current === requestTargetKey ? null : requestTargetKey));
  }, [requestTargetKey]);

  const event: EventDetailsItem | null = details?.event ?? null;
  const interest: InterestState | null = details?.interest ?? null;
  const enrichment: EnrichmentStatus | null = details?.enrichment ?? null;
  const attribution: string | null = details?.attribution ?? null;

  const ticketGroups: TicketLinkGroups = useMemo(
    () => splitTicketLinks(event?.ticketLinks),
    [event?.ticketLinks],
  );

  const mapsUrl: string | null = useMemo(
    () =>
      event
        ? buildMapsUrl({
            mapUrl: event.mapUrl,
            venueName: event.venue?.name,
            address: event.address,
            latitude: event.latitude,
            longitude: event.longitude,
          })
        : null,
    [event],
  );

  return {
    ready,
    session,
    localityKey,
    eventKey,
    status,
    event,
    interest,
    enrichment,
    attribution,
    ticketGroups,
    mapsUrl,
    descriptionExpanded,
    toggleDescriptionExpanded,
    saving,
    actionError,
    error,
    reload,
    toggleInterest,
    setReminder,
    openLink,
    navigateBack,
  };
}
