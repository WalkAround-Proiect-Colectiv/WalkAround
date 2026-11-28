import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Platform, Pressable, ScrollView, Share, StyleSheet, Text, View } from 'react-native';
import * as Linking from 'expo-linking';
import { Redirect, useLocalSearchParams, useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { ArrowLeft, Link2, MessageCircle, Pencil, Plus, RefreshCw, UsersRound } from 'lucide-react-native';
import { useSession } from '../../login/components/SessionProvider';
import GroupPicture from '../components/GroupPicture';
import TripPlanningScreen from './TripPlanningScreen';
import GroupChatScreen from './GroupChatScreen';
import {
  createGroupInvite, getTripGroup, GroupRole, listGroupInvites, listGroupPlans, listTripGroups,
  makeGroupPlanCurrent, revokeGroupInvite,
  type CreatedTripGroup, type TripGroupInvite, type TripGroupInviteSummary,
} from '../api/tripGroups';
import type { SavedTripPlan } from '../api/tripPlanning';
import { subscribeToGroupPlanSaves } from '../api/groupPlanNotifications';

const BLUE = '#0065EB';

function invitationStatus(invite: TripGroupInviteSummary): string {
  if (invite.revokedAt) return 'Revoked';
  if (invite.usedAt) return 'Used';
  if (Date.parse(invite.expiresAt) <= Date.now()) return 'Expired';
  return 'Active';
}

export default function TripGroupsScreen() {
  const { session, ready } = useSession();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const params = useLocalSearchParams<{ groupId?: string; tab?: string; planId?: string }>();
  const groupId = typeof params.groupId === 'string' ? params.groupId : null;
  const planning = params.tab === 'plan';
  const chatting = params.tab === 'chat';
  const [groups, setGroups] = useState<CreatedTripGroup[]>([]);
  const [selected, setSelected] = useState<CreatedTripGroup | null>(null);
  const [invitations, setInvitations] = useState<TripGroupInviteSummary[]>([]);
  const [plans, setPlans] = useState<SavedTripPlan[]>([]);
  const [selectingPlanId, setSelectingPlanId] = useState<string | null>(null);
  const [refreshingPlans, setRefreshingPlans] = useState(false);
  const [newInvite, setNewInvite] = useState<TripGroupInvite | null>(null);
  const [busy, setBusy] = useState(true);
  const [creating, setCreating] = useState(false);
  const [revokingId, setRevokingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const planRequestId = useRef(0);

  async function reloadPlans(targetGroupId: string) {
    const requestId = ++planRequestId.current;
    const found = await listGroupPlans(targetGroupId);
    if (requestId === planRequestId.current && targetGroupId === groupId) setPlans(found);
  }

  useEffect(() => {
    if (!session) return;
    let active = true;
    void listTripGroups()
      .then((results) => { if (active) setGroups(results); })
      .catch((reason) => { if (active) setError(reason instanceof Error ? reason.message : 'Could not load groups.'); })
      .finally(() => { if (active && !groupId) setBusy(false); });
    return () => { active = false; };
  }, [session, groupId]);

  useEffect(() => {
    const requestId = ++planRequestId.current;
    if (!session || !groupId) {
      setSelected(null);
      setInvitations([]);
      setPlans([]);
      setNewInvite(null);
      setBusy(false);
      return;
    }
    let active = true;
    setBusy(true);
    setError(null);
    setSelected(null);
    setInvitations([]);
    setPlans([]);
    setNewInvite(null);
    void getTripGroup(groupId)
      .then(async (group) => {
        if (!active) return;
        setSelected(group);
        const [foundPlans, foundInvites] = await Promise.all([
          listGroupPlans(groupId),
          group.memberRole === GroupRole.ADMIN ? listGroupInvites(groupId) : Promise.resolve([]),
        ]);
        if (active) {
          if (requestId === planRequestId.current) setPlans(foundPlans);
          setInvitations(foundInvites);
        }
      })
      .catch((reason) => { if (active) setError(reason instanceof Error ? reason.message : 'Could not load group.'); })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [session, groupId, params.planId]);

  useEffect(() => {
    if (!session || !groupId) return;
    let active = true;

    const unsubscribe = subscribeToGroupPlanSaves((saved) => {
      if (saved.groupId !== groupId) return;
      void reloadPlans(groupId).catch((reason) => {
        if (active) setError(reason instanceof Error ? reason.message : 'Could not load the new group plan.');
      });
    });
    return () => { active = false; unsubscribe(); };
  }, [session?.user.id, groupId]);

  async function createInvite() {
    if (!groupId || creating) return;
    setCreating(true);
    setError(null);
    setNotice(null);
    try {
      const result = await createGroupInvite(groupId);
      setNewInvite(result);
      setInvitations(await listGroupInvites(groupId));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not create an invitation.');
    } finally {
      setCreating(false);
    }
  }

  async function shareInvite() {
    if (!newInvite) return;
    const link = Linking.createURL('/trip-group-invite') + '#token=' + newInvite.token;
    try {
      if (Platform.OS === 'web') {
        await navigator.clipboard.writeText(link);
        setNotice('Invitation link copied.');
      } else {
        await Share.share({ message: link });
      }
    } catch {
      setNotice('Select and copy the link below to share it.');
    }
  }

  async function revoke(invitationId: string) {
    if (!groupId || revokingId) return;
    setRevokingId(invitationId);
    setError(null);
    try {
      await revokeGroupInvite(groupId, invitationId);
      if (newInvite?.id === invitationId) setNewInvite(null);
      setInvitations(await listGroupInvites(groupId));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not revoke the invitation.');
    } finally {
      setRevokingId(null);
    }
  }

  async function makeCurrent(planId: string) {
    if (!groupId || selectingPlanId) return;
    setSelectingPlanId(planId);
    setError(null);
    try {
      await makeGroupPlanCurrent(groupId, planId);
      await reloadPlans(groupId);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not select this plan.');
    } finally {
      setSelectingPlanId(null);
    }
  }

  async function refreshPlans() {
    if (!groupId || refreshingPlans) return;
    setRefreshingPlans(true);
    setError(null);
    try {
      await reloadPlans(groupId);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not refresh group plans.');
    } finally {
      setRefreshingPlans(false);
    }
  }

  if (!ready) return <View style={styles.center}><ActivityIndicator color={BLUE} /></View>;
  if (!session) return <Redirect href="/login" />;

  const link = newInvite ? Linking.createURL('/trip-group-invite') + '#token=' + newInvite.token : null;
  const currentPlan = plans.find((plan) => plan.current);
  const openedPlan = plans.find((plan) => plan.id === params.planId);

  if (planning && selected?.id === groupId && !busy) return <TripPlanningScreen key={openedPlan?.id ?? 'new'} group={selected}
    initialPlan={openedPlan}
    onGroupPlanSaved={(plan) => {
      planRequestId.current += 1;
      setPlans((current) => [plan, ...current.filter((item) => item.id !== plan.id)]);
    }} />;

  if (chatting && selected?.id === groupId && !busy) return <GroupChatScreen key={selected.id} group={selected} />;

  return <View style={styles.screen}>
    <ScrollView contentContainerStyle={[styles.content, {
      paddingTop: Math.max(insets.top, 16), paddingBottom: Math.max(insets.bottom, 24) + 24,
    }]}>
      <View style={styles.header}>
        <Pressable accessibilityRole="button" accessibilityLabel={groupId ? 'Back to your groups' : 'Back to trip planner'}
          onPress={() => groupId ? router.replace('/trip-groups') : router.push('/trip-plan')}
          style={styles.back}><ArrowLeft size={21} color="#202632" /></Pressable>
        <Text style={styles.brand}>WalkAround</Text><View style={styles.spacer} />
      </View>

      <View style={styles.heading}>
        {selected?.pictureUrl ? <GroupPicture groupId={selected.id} size={58} />
          : <View style={styles.icon}><UsersRound size={25} color={BLUE} /></View>}
        <Text accessibilityRole="header" style={styles.title}>{selected?.name ?? 'Your groups'}</Text>
        <Text style={styles.hint}>{selected ? 'Your shared trip space' : 'Find the people you plan trips with.'}</Text>
      </View>

      {busy && <ActivityIndicator color={BLUE} />}
      {error && <Text accessibilityRole="alert" style={styles.error}>{error}</Text>}
      {notice && <Text accessibilityLiveRegion="polite" style={styles.notice}>{notice}</Text>}

      {!groupId && !busy && <>
        <Pressable accessibilityRole="button" onPress={() => router.push('/trip-group-create')}
          style={styles.primaryButton}><Plus size={18} color="#fff" /><Text style={styles.primaryText}>Create a group</Text></Pressable>
        {groups.length === 0 ? <View style={styles.card}><Text style={styles.hint}>You are not in a trip group yet.</Text></View>
          : groups.map((group) => <Pressable key={group.id} accessibilityRole="button"
            onPress={() => router.push({ pathname: '/trip-groups', params: { groupId: group.id } })}
            style={styles.groupCard}>
            {group.pictureUrl ? <GroupPicture groupId={group.id} size={42} />
              : <View style={styles.groupIcon}><UsersRound size={20} color={BLUE} /></View>}
            <View style={styles.groupText}><Text style={styles.groupName}>{group.name}</Text>
              <Text style={styles.hint}>{group.memberRole === GroupRole.ADMIN ? 'Admin' : 'Member'}</Text></View>
          </Pressable>)}
      </>}

      {selected && !busy && <>
        <Pressable accessibilityRole="button" onPress={() => router.push({
          pathname: '/trip-groups', params: { groupId: selected.id, tab: 'plan' },
        })} style={styles.primaryButton}>
          <Plus size={18} color="#fff" /><Text style={styles.primaryText}>Plan a group trip</Text>
        </Pressable>
        <View style={styles.card}>
          <View style={styles.planHeading}>
            <Text style={styles.sectionTitle}>Group trip plans</Text>
            <Pressable accessibilityRole="button" accessibilityLabel="Refresh group plans"
              disabled={refreshingPlans} onPress={() => { void refreshPlans(); }}>
              {refreshingPlans ? <ActivityIndicator color={BLUE} size="small" />
                : <RefreshCw size={19} color={BLUE} />}
            </Pressable>
          </View>
          {currentPlan ? <>
            <Text style={styles.groupName}>{currentPlan.title} · Current plan</Text>
            <Text style={styles.body}>{currentPlan.departureCity} → {currentPlan.destinationCity}</Text>
            <Text style={styles.hint}>{currentPlan.transportMode.toLowerCase()} · {currentPlan.adults} adult{currentPlan.adults === 1 ? '' : 's'} · {currentPlan.stayingNights} nights</Text>
            <Text style={styles.hint}>Departure: {currentPlan.departureFrom.replace('T', ' ').slice(0, 16)} to {currentPlan.departureUntil.replace('T', ' ').slice(0, 16)}</Text>
            <Text style={styles.hint}>Budgets: €{currentPlan.transportBudget} transport · €{currentPlan.accommodationBudget} stay</Text>
            {currentPlan.description && <Text style={styles.body}>{currentPlan.description}</Text>}
            <Pressable accessibilityRole="button" onPress={() => router.push({
              pathname: '/trip-groups', params: { groupId: selected.id, tab: 'plan', planId: currentPlan.id },
            })} style={styles.editButton}>
              <Text style={styles.editText}>Open current preset</Text>
            </Pressable>
          </> : <Text style={styles.hint}>No group plan saved yet. Start a search and save its preset here.</Text>}
          {plans.filter((plan) => !plan.current).map((plan) => <View key={plan.id} style={styles.invitationRow}>
            <View style={styles.groupText}>
              <Text style={styles.body}>{plan.title}</Text>
              <Text style={styles.hint}>{plan.departureCity} → {plan.destinationCity} · {plan.adults} adults</Text>
              <Pressable accessibilityRole="button" onPress={() => router.push({
                pathname: '/trip-groups', params: { groupId: selected.id, tab: 'plan', planId: plan.id },
              })}><Text style={styles.editText}>Open preset</Text></Pressable>
            </View>
            {selected.memberRole === GroupRole.ADMIN && <Pressable accessibilityRole="button"
              disabled={Boolean(selectingPlanId)} onPress={() => { void makeCurrent(plan.id); }}>
              <Text style={styles.editText}>{selectingPlanId === plan.id ? 'Selecting…' : 'Set current'}</Text>
            </Pressable>}
          </View>)}
        </View>
        {selected.description && <View style={styles.card}><Text style={styles.body}>{selected.description}</Text></View>}
        <View style={styles.card}>
          <Text style={styles.sectionTitle}>Your role</Text>
          <Text style={styles.body}>{selected.memberRole === GroupRole.ADMIN ? 'Admin' : 'Member'}</Text>
        </View>
        {selected.memberRole === GroupRole.ADMIN && <Pressable accessibilityRole="button"
          onPress={() => router.push({ pathname: '/trip-group-edit', params: { groupId: selected.id } })}
          style={styles.editButton}>
          <Pencil size={18} color={BLUE} /><Text style={styles.editText}>Edit group</Text>
        </Pressable>}
        {selected.memberRole === GroupRole.ADMIN && <View style={styles.card}>
          <Text style={styles.sectionTitle}>Invite someone</Text>
          <Text style={styles.hint}>Each link can be used once. You can revoke it before it is used.</Text>
          <Pressable accessibilityRole="button" disabled={creating} onPress={() => { void createInvite(); }}
            style={[styles.primaryButton, creating && styles.disabled]}>
            {creating ? <ActivityIndicator color="#fff" /> : <Link2 size={18} color="#fff" />}
            <Text style={styles.primaryText}>{creating ? 'Creating link…' : 'Create invitation link'}</Text>
          </Pressable>
          {link && <View style={styles.linkBox}>
            <Text style={styles.hint}>This link is shown only now. Share it before leaving this page.</Text>
            <Text style={styles.hint}>Expires {new Date(newInvite!.expiresAt).toLocaleString()}.</Text>
            <Text selectable style={styles.link}>{link}</Text>
            <Pressable accessibilityRole="button" onPress={() => { void shareInvite(); }}
              style={styles.secondaryButton}><Text style={styles.secondaryText}>Copy or share link</Text></Pressable>
          </View>}
          {invitations.length > 0 && <Text style={styles.sectionTitle}>Recent invitations</Text>}
          {invitations.map((invite) => <View key={invite.id} style={styles.invitationRow}>
            <View style={styles.groupText}>
              <Text style={styles.body}>{invitationStatus(invite)}</Text>
              <Text style={styles.hint}>Expires {new Date(invite.expiresAt).toLocaleString()}</Text>
            </View>
            {invitationStatus(invite) === 'Active' && <Pressable accessibilityRole="button"
              disabled={Boolean(revokingId)} onPress={() => { void revoke(invite.id); }}>
              <Text style={styles.revoke}>{revokingId === invite.id ? 'Revoking…' : 'Revoke'}</Text>
            </Pressable>}
          </View>)}
        </View>}
      </>}
    </ScrollView>
    {selected && !busy && <Pressable accessibilityRole="button" accessibilityLabel="Open group chat"
      onPress={() => router.push({ pathname: '/trip-groups', params: { groupId: selected.id, tab: 'chat' } })}
      style={[styles.chatButton, { bottom: Math.max(insets.bottom, 12) + 18 }]}>
      <MessageCircle size={25} color="#fff" />
    </Pressable>}
  </View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#F4F7FB' },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  content: { paddingHorizontal: 16, width: '100%', maxWidth: 620, alignSelf: 'center', gap: 16 },
  header: { height: 54, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  back: { width: 42, height: 42, borderRadius: 15, backgroundColor: '#fff', alignItems: 'center', justifyContent: 'center' },
  brand: { color: BLUE, fontSize: 18, fontWeight: '800' },
  spacer: { width: 42 },
  heading: { alignItems: 'center', gap: 8, paddingVertical: 12 },
  icon: { width: 54, height: 54, borderRadius: 18, backgroundColor: '#EAF3FF', alignItems: 'center', justifyContent: 'center' },
  title: { color: '#202632', fontSize: 27, fontWeight: '800', textAlign: 'center' },
  hint: { color: '#788596', fontSize: 13, lineHeight: 19 },
  card: { backgroundColor: '#fff', borderRadius: 22, borderWidth: 1, borderColor: '#E8EDF4', padding: 18, gap: 12 },
  groupCard: { backgroundColor: '#fff', borderRadius: 18, padding: 15, flexDirection: 'row', alignItems: 'center', gap: 12 },
  groupIcon: { width: 42, height: 42, borderRadius: 14, backgroundColor: '#EAF3FF', alignItems: 'center', justifyContent: 'center' },
  groupText: { flex: 1 },
  groupName: { color: '#202632', fontSize: 17, fontWeight: '800' },
  sectionTitle: { color: '#202632', fontSize: 17, fontWeight: '800' },
  planHeading: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  body: { color: '#4C5A6C', fontSize: 14, lineHeight: 20 },
  primaryButton: { minHeight: 50, backgroundColor: BLUE, borderRadius: 15, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  primaryText: { color: '#fff', fontSize: 14, fontWeight: '800' },
  editButton: { minHeight: 48, borderWidth: 1, borderColor: '#B5D1FA', borderRadius: 15,
    flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  editText: { color: BLUE, fontSize: 14, fontWeight: '800' },
  secondaryButton: { minHeight: 44, borderWidth: 1, borderColor: '#B5D1FA', borderRadius: 13, alignItems: 'center', justifyContent: 'center' },
  secondaryText: { color: BLUE, fontSize: 13, fontWeight: '800' },
  linkBox: { backgroundColor: '#F3F8FF', borderRadius: 14, padding: 12, gap: 10 },
  link: { color: BLUE, fontSize: 12, lineHeight: 18 },
  invitationRow: { flexDirection: 'row', alignItems: 'center', borderTopWidth: 1, borderColor: '#ECF0F5', paddingTop: 12, gap: 8 },
  revoke: { color: '#C51B2D', fontWeight: '700', fontSize: 13 },
  error: { color: '#C51B2D', fontSize: 13, lineHeight: 19 },
  notice: { color: '#184B94', fontSize: 13, lineHeight: 19 },
  disabled: { opacity: 0.5 },
  chatButton: { position: 'absolute', right: 20, width: 56, height: 56, borderRadius: 20,
    backgroundColor: BLUE, alignItems: 'center', justifyContent: 'center', elevation: 5,
    shadowColor: '#000', shadowOpacity: 0.18, shadowRadius: 8, shadowOffset: { width: 0, height: 3 } },
});
