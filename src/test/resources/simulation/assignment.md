# Assignment parent-teacher conferences

## In short

We're asking you to build a small service that schools use to plan parent-teacher conferences. Spend no more than four hours on it. Afterwards we'll go over what you built and why you built it that way, together in a Zoom video call.

We're mostly curious about how you tackle the assignment. We want to see which choices you make, how you explain them and how you defend them. You build it in Kotlin or TypeScript, and you're allowed to use AI.

## The situation

A few times a year, schools hold an evening of short conversations between teachers and parents. Many schools still plan these with paper lists, a shared spreadsheet or even a letter with a fixed time. That leads to double bookings, parents who don't know when to show up and teachers who are still puzzling out on the evening itself who's coming by.

You build the backend that lets a teacher set up such an evening themselves and lets parents pick a time themselves.

## What it should be able to do

1. A teacher creates a conference evening with a date, a start and end time and the length of a conversation. The time slots are created from that.
2. A time slot has a maximum number of conversations. By default that's one.
3. A parent sees the free time slots of an evening and books one for a child.
4. A parent can move a booking to another time slot, or cancel it.
5. A child can have more than one parent. Every parent of the child sees the appointment.
6. A teacher sees an overview of the evening: which child comes when, and with which parent.
7. A teacher can remove a time slot, for example for a break.

Not everything is described in detail. Where you have to make a choice yourself, make it and write it down. You're also free to add functionality you think is interesting yourself, as long as you write down the decisions you make. If you can't figure it out on your own, just send me an email!

## Ground rules

- **Time.** Spend no more than four hours on it. If you don't get everything done, that's fine. Write down what you left out and why.
- **Language and tools.** Build it in Kotlin or TypeScript (Node.js). Those are the languages we work in. You're free to choose which frameworks you use.
- **AI.** You may use AI tools. In the interview we will ask you to explain all code and decisions, including the code you didn't write yourself. We're also very curious what you do and don't use AI for.
- **Storage.** Choose for yourself how you store the data. A database, a file or in memory are all fine, as long as you can explain the choice.
- **Login.** A real login isn't needed. Assume that every request already tells you who the user is, and whether they're a teacher or a parent.
- **Test data.** You don't need to be able to create or manage teachers, parents and children. A few fixed examples in the code or in the database is enough.
- **UI.** An API is enough. A UI is allowed, but not required. If you do build one, we'd also like to look at how you thought about the user experience.
- **Tests.** Tests are welcome, but not required. If you leave them out, tell us in the interview how you would test it.
- **Questions.** You can always ask us questions about the assignment.

## What you hand in

No later than two working days before the interview, send us a link to a repository with:

- **The code**, set up in way that makes it easy for us to test
- A **README** that explains in a few steps how we start it and try it out.
- A **DECISIONS.md** file of no more than two pages
  - the choices you made, and why.
  - what you deliberately left out.
  - what you think goes wrong first if this goes live tomorrow at a hundred schools.
  - If you used AI, we'd also like to see here how and what you used it for. Do you have an orchestrator spin up a whole army of agents? Or do you use it as a pair programming partner?

## The interview

The interview takes an hour and a half and happens over Zoom. Because we work remotely as a team, this immediately gives us a good impression of how you'd fit into our team. Make sure you can share your screen and that the code is already running locally, so you don't have to start everything up during the interview.

1. In about twenty minutes, you show us what you built.
2. We go through your choices together and also look at a few pieces of code we find interesting.
3. We give you a new feature request and want to hear from you how you'd approach it. No worries, you don't have to do any live coding.

Of course there's also plenty of room in the interview to ask us questions. We're hoping for a relaxed, friendly conversation, where you're just as curious about us as we are about you.

## Finally

We're well aware that doing this assignment costs you time. That's why we only ask candidates who are serious contenders for the role to do it. We always go through the assignment you made together with you to give you feedback, even if we don't move forward with you.
