-- PRiME Lifecycle Verification Model
-- L1: Consumable Depletion Invariant  
-- L5: Parallel Branch Resource Exclusion
module PRiMELifecycle

sig Material {}

sig Activity {
  consumes: Material -> Int,
  produces: Material -> Int
}

sig Inventory {
  initial: Material -> Int
}

sig ParallelGateway {
  branches: set Activity
}

sig ExclusiveTool {}

sig ToolRequirement {
  activity: Activity,
  tool: ExclusiveTool
}

sig SeqOrder {
  stepBefore: Activity,
  stepAfter: Activity
}

pred depletionSafe[inv: Inventory] {
  all m: Material |
    inv.initial[m] + (sum a: Activity | a.produces[m]) >=
    (sum a: Activity | a.consumes[m])
}

pred serialized[a1, a2: Activity] {
  some s: SeqOrder |
    (s.stepBefore = a1 and s.stepAfter = a2) or
    (s.stepBefore = a2 and s.stepAfter = a1)
}

pred L1_holds {
  all inv: Inventory | depletionSafe[inv]
}

pred L5_holds {
  all g: ParallelGateway |
    all disj b1, b2: g.branches |
    all t: ExclusiveTool |
      (some r1: ToolRequirement | r1.activity = b1 and r1.tool = t) and
      (some r2: ToolRequirement | r2.activity = b2 and r2.tool = t)
      implies serialized[b1, b2]
}

